package pl.mkn.tdw.frontendcatalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextReadModelValidator;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static pl.mkn.tdw.common.GitLabPathUtils.relativeProjectPath;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.CODE_SEARCH_MODE_PATH_PREFIXES;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.CODE_SEARCH_MODE_WHOLE_REPOSITORY;

@Service
@RequiredArgsConstructor
public class FrontendApplicationCatalogService {

    private static final String FRONTEND = "frontend";
    private static final String INTERNAL_SERVICE = "internal-service";
    private static final Set<String> FRONTEND_VALIDATION_CODES = Set.of(
            "INTERNAL_SERVICE_SUBTYPE_REQUIRED",
            "INTERNAL_SERVICE_SUBTYPE_UNSUPPORTED",
            "INTERNAL_SERVICE_SUBTYPE_UNKNOWN",
            "FRONTEND_WITHOUT_CODE_SEARCH_SCOPE",
            "FRONTEND_WITH_MULTIPLE_CODE_SEARCH_SCOPES",
            "FRONTEND_SCOPE_WITHOUT_PRIMARY_REPOSITORY",
            "FRONTEND_SCOPE_WITH_MULTIPLE_PRIMARY_REPOSITORIES",
            "FRONTEND_PRIMARY_REPOSITORY_TYPE_MISMATCH",
            "UNKNOWN_CODE_SEARCH_REPOSITORY",
            "CODE_SEARCH_REPOSITORY_WITHOUT_SEARCH_MODE",
            "CODE_SEARCH_REPOSITORY_UNKNOWN_SEARCH_MODE",
            "CODE_SEARCH_REPOSITORY_PATH_PREFIXES_EMPTY",
            "CODE_SEARCH_REPOSITORY_WHOLE_REPOSITORY_WITH_PATH_PREFIXES",
            "CODE_SEARCH_REPOSITORY_INVALID_PATH_PREFIX"
    );

    private final OperationalContextPort operationalContextPort;
    private final OperationalContextReadModelValidator validator = new OperationalContextReadModelValidator();

    public FrontendApplicationCatalog loadCatalog() {
        var readSession = operationalContextPort.capture();
        var catalog = readSession.snapshot().catalog();
        var repositoriesById = repositoriesById(catalog);
        var scopesBySystemId = scopesBySystemId(catalog);
        var registrations = new ArrayList<FrontendApplicationRegistration>();
        var additionalFindings = new ArrayList<FrontendConfigurationFinding>();

        for (var system : catalog.systems()) {
            if (isFrontend(system)) {
                resolveRegistration(system, scopesBySystemId, repositoriesById, additionalFindings)
                        .ifPresent(registrations::add);
            }
        }
        registrations.sort(Comparator.comparing(FrontendApplicationRegistration::label)
                .thenComparing(FrontendApplicationRegistration::systemId));

        var findings = new ArrayList<>(configurationFindings(catalog, scopesBySystemId, repositoriesById));
        findings.addAll(additionalFindings);
        findings.sort(Comparator.comparing(FrontendConfigurationFinding::severity)
                .thenComparing(FrontendConfigurationFinding::code)
                .thenComparing(finding -> valueOrEmpty(finding.entityId())));
        return new FrontendApplicationCatalog(readSession.contentDigest(), registrations, findings);
    }

    private Optional<FrontendApplicationRegistration> resolveRegistration(
            OperationalContextSystem system,
            Map<String, List<OperationalContextRepositorySearchScope>> scopesBySystemId,
            Map<String, OperationalContextRepository> repositoriesById,
            List<FrontendConfigurationFinding> findings
    ) {
        var scopes = scopesBySystemId.getOrDefault(system.id(), List.of());
        if (scopes.size() != 1) return Optional.empty();
        var primaryRepositories = scopes.get(0).repositories().stream()
                .filter(repository -> "primary".equals(normalize(repository.role())))
                .toList();
        if (primaryRepositories.size() != 1) return Optional.empty();
        var primary = primaryRepositories.get(0);
        var repository = repositoriesById.get(primary.repoId());
        if (repository == null || !FRONTEND.equals(normalize(repository.repositoryType()))
                || !validSearchBoundary(primary)) return Optional.empty();
        if (!StringUtils.hasText(repository.git().projectPath())) {
            findings.add(finding("FRONTEND_PRIMARY_REPOSITORY_PROJECT_PATH_REQUIRED",
                    "Primary frontend repository " + repository.id() + " requires git.projectPath.", repository.id()));
            return Optional.empty();
        }
        var coordinates = gitLabCoordinates(repository);
        if (coordinates.isEmpty()) {
            findings.add(finding("FRONTEND_PRIMARY_REPOSITORY_GITLAB_COORDINATES_REQUIRED",
                    "Primary frontend repository " + repository.id()
                            + " requires resolvable GitLab group and project coordinates.", repository.id()));
            return Optional.empty();
        }
        return Optional.of(new FrontendApplicationRegistration(
                system.id(), label(system), system.summary(), repository.id(), repository.git().projectPath(),
                coordinates.get().group(), coordinates.get().projectName(), repository.git().defaultBranch(),
                primary.searchMode(), primary.pathPrefixes()
        ));
    }

    private FrontendConfigurationFinding finding(String code, String message, String repositoryId) {
        return new FrontendConfigurationFinding("error", code, message, "repository", repositoryId);
    }

    private Optional<GitLabCoordinates> gitLabCoordinates(OperationalContextRepository repository) {
        var git = repository.git();
        if (!"gitlab".equals(git.provider()) || !StringUtils.hasText(git.projectPath())) return Optional.empty();
        var projectPath = git.projectPath().trim().replace('\\', '/');
        var group = StringUtils.hasText(git.group()) ? trimSlashes(git.group()) : null;
        var projectName = StringUtils.hasText(group) ? relativeProjectPath(group, projectPath) : null;
        if (!StringUtils.hasText(group)) {
            var separator = projectPath.lastIndexOf('/');
            if (separator > 0 && separator < projectPath.length() - 1) {
                group = trimSlashes(projectPath.substring(0, separator));
                projectName = trimSlashes(projectPath.substring(separator + 1));
            }
        }
        return StringUtils.hasText(group) && StringUtils.hasText(projectName)
                ? Optional.of(new GitLabCoordinates(group, projectName)) : Optional.empty();
    }

    private List<FrontendConfigurationFinding> configurationFindings(
            OperationalContextCatalog catalog,
            Map<String, List<OperationalContextRepositorySearchScope>> scopesBySystemId,
            Map<String, OperationalContextRepository> repositoriesById
    ) {
        var systemIds = catalog.systems().stream().filter(this::isFrontend).map(OperationalContextSystem::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var scopeIds = systemIds.stream().flatMap(id -> scopesBySystemId.getOrDefault(id, List.of()).stream())
                .map(OperationalContextRepositorySearchScope::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var repositoryIds = systemIds.stream().flatMap(id -> scopesBySystemId.getOrDefault(id, List.of()).stream())
                .flatMap(scope -> scope.repositories().stream()).map(OperationalContextRepositorySearchRepository::repoId)
                .filter(repositoriesById::containsKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return validator.validate(catalog).stream()
                .filter(value -> FRONTEND_VALIDATION_CODES.contains(value.code()))
                .filter(value -> isFrontendFinding(value, systemIds, scopeIds, repositoryIds))
                .map(this::configurationFinding).toList();
    }

    private boolean isFrontendFinding(ValidationFinding finding, Set<String> systems, Set<String> scopes,
                                      Set<String> repositories) {
        if (finding.code().startsWith("INTERNAL_SERVICE_SUBTYPE_")) return true;
        return finding.sourceRefs().stream().anyMatch(ref -> switch (ref.entityType()) {
            case "system" -> systems.contains(ref.entityId());
            case "code-search-scope" -> scopes.contains(ref.entityId());
            case "repository" -> repositories.contains(ref.entityId());
            default -> false;
        });
    }

    private FrontendConfigurationFinding configurationFinding(ValidationFinding finding) {
        var ref = finding.sourceRefs().stream().findFirst().orElse(null);
        return new FrontendConfigurationFinding(finding.severity(), finding.code(), finding.message(),
                ref != null ? ref.entityType() : null, ref != null ? ref.entityId() : null);
    }

    private Map<String, OperationalContextRepository> repositoriesById(OperationalContextCatalog catalog) {
        var result = new LinkedHashMap<String, OperationalContextRepository>();
        catalog.repositories().forEach(repository -> {
            if (StringUtils.hasText(repository.id())) result.putIfAbsent(repository.id(), repository);
        });
        return Map.copyOf(result);
    }

    private Map<String, List<OperationalContextRepositorySearchScope>> scopesBySystemId(OperationalContextCatalog catalog) {
        return catalog.codeSearchScopes().stream()
                .filter(scope -> "system".equals(normalize(scope.target().type())))
                .filter(scope -> StringUtils.hasText(scope.target().id()))
                .collect(java.util.stream.Collectors.groupingBy(scope -> scope.target().id(), LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private boolean isFrontend(OperationalContextSystem system) {
        return INTERNAL_SERVICE.equals(normalize(system.systemType())) && FRONTEND.equals(normalize(system.systemSubtype()));
    }

    private boolean validSearchBoundary(OperationalContextRepositorySearchRepository repository) {
        var mode = normalize(repository.searchMode());
        return CODE_SEARCH_MODE_WHOLE_REPOSITORY.equals(mode) && repository.pathPrefixes().isEmpty()
                || CODE_SEARCH_MODE_PATH_PREFIXES.equals(mode) && !repository.pathPrefixes().isEmpty();
    }

    private String label(OperationalContextSystem system) {
        if (StringUtils.hasText(system.name())) return system.name().trim();
        if (StringUtils.hasText(system.shortName())) return system.shortName().trim();
        return system.id();
    }

    private String trimSlashes(String value) {
        return value.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static String valueOrEmpty(String value) { return value != null ? value : ""; }

    private record GitLabCoordinates(String group, String projectName) {}
}

