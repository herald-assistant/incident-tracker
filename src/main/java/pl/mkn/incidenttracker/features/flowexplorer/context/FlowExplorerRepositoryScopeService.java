package pl.mkn.incidenttracker.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.features.flowexplorer.FlowExplorerProperties;
import pl.mkn.incidenttracker.features.flowexplorer.endpoint.FlowExplorerGitLabConfigurationException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;

@Service
@RequiredArgsConstructor
public class FlowExplorerRepositoryScopeService {

    private static final Set<OperationalContextEntryType> REPOSITORY_SCOPE_ENTRY_TYPES = Set.of(
            SYSTEM,
            REPOSITORY
    );

    private final OperationalContextPort operationalContextPort;
    private final GitLabProperties gitLabProperties;
    private final FlowExplorerProperties flowExplorerProperties;

    public FlowExplorerRepositoryScope resolve(String systemId, String branch) {
        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                REPOSITORY_SCOPE_ENTRY_TYPES,
                List.of(),
                false
        ));
        var system = findSystem(catalog, systemId);
        var requestedBranch = normalize(branch);
        var resolvedRef = resolveBranch(requestedBranch);
        var gitLabGroup = requiredGitLabGroup();
        var repositoriesById = repositoriesById(catalog.repositories());
        var repositoryRefs = repositoryRefs(catalog, system);
        var repositories = new ArrayList<FlowExplorerRepositoryScopeRepository>();
        var limitations = new ArrayList<String>();

        if (repositoryRefs.isEmpty()) {
            limitations.add("Operational context system has no repository references or code-search scope repositories.");
        }

        for (var repositoryRef : repositoryRefs) {
            var repository = repositoriesById.get(normalizeComparable(repositoryRef.repositoryId()));
            if (repository == null) {
                limitations.add("Operational context references unknown repository: " + repositoryRef.repositoryId());
                continue;
            }
            if (!gitLabRepository(repository)) {
                limitations.add("Repository " + repository.id() + " is not a GitLab repository.");
                continue;
            }
            if (!groupMatches(gitLabGroup, repository)) {
                limitations.add("Repository " + repository.id()
                        + " belongs to GitLab group " + repository.git().group()
                        + ", not configured group " + gitLabGroup + ".");
                continue;
            }

            var projectName = projectName(gitLabGroup, repository);
            if (!StringUtils.hasText(projectName)) {
                limitations.add("Repository " + repository.id() + " has no GitLab project name or projectPath.");
                continue;
            }

            repositories.add(new FlowExplorerRepositoryScopeRepository(
                    repository.id(),
                    projectName,
                    projectPath(repository),
                    repositoryRef.priority(),
                    repositoryRef.reason(),
                    repository
            ));
        }

        return new FlowExplorerRepositoryScope(
                system,
                requestedBranch,
                resolvedRef,
                gitLabGroup,
                repositoryRefs.size(),
                repositories,
                distinct(limitations)
        );
    }

    private OperationalContextSystem findSystem(OperationalContextCatalog catalog, String systemId) {
        var normalizedSystemId = normalizeComparable(systemId);
        return catalog.systems().stream()
                .filter(system -> normalizeComparable(system.id()).equals(normalizedSystemId))
                .findFirst()
                .orElseThrow(() -> new FlowExplorerSystemNotFoundException(systemId));
    }

    private String resolveBranch(String requestedBranch) {
        if (StringUtils.hasText(requestedBranch)) {
            return requestedBranch;
        }
        var defaultBranch = normalize(flowExplorerProperties.getDefaultBranch());
        return StringUtils.hasText(defaultBranch) ? defaultBranch : "main";
    }

    private String requiredGitLabGroup() {
        var group = normalize(gitLabProperties.getGroup());
        if (!StringUtils.hasText(group)) {
            throw new FlowExplorerGitLabConfigurationException("analysis.gitlab.group");
        }
        return trimSlashes(group);
    }

    private Map<String, OperationalContextRepository> repositoriesById(List<OperationalContextRepository> repositories) {
        var repositoriesById = new LinkedHashMap<String, OperationalContextRepository>();
        for (var repository : repositories) {
            var normalizedId = normalizeComparable(repository.id());
            if (StringUtils.hasText(normalizedId)) {
                repositoriesById.putIfAbsent(normalizedId, repository);
            }
        }
        return repositoriesById;
    }

    private List<RepositoryRef> repositoryRefs(
            OperationalContextCatalog catalog,
            OperationalContextSystem system
    ) {
        var refsById = new LinkedHashMap<String, RepositoryRef>();
        addRepositoryRefs(refsById, system.references().repositories(), null, "system.references.repositories");
        addRepositoryRefs(refsById, system.codeSearchScope().repositories(), null, "system.codeSearchScope.repositories");

        for (var scope : catalog.codeSearchScopes()) {
            if (!targetsSystem(scope, system.id())) {
                continue;
            }
            var scopeRepositories = scope.repositories().stream()
                    .sorted(Comparator
                            .comparing(OperationalContextRepositorySearchRepository::priority,
                                    Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(OperationalContextRepositorySearchRepository::repoId,
                                    Comparator.nullsLast(String::compareTo)))
                    .toList();
            for (var repository : scopeRepositories) {
                addRepositoryRef(
                        refsById,
                        repository.repoId(),
                        repository.priority(),
                        "codeSearchScopes[" + scope.id() + "]"
                );
            }
        }

        return List.copyOf(refsById.values());
    }

    private void addRepositoryRefs(
            LinkedHashMap<String, RepositoryRef> refsById,
            List<String> repositoryIds,
            Integer priority,
            String reason
    ) {
        for (var repositoryId : repositoryIds != null ? repositoryIds : List.<String>of()) {
            addRepositoryRef(refsById, repositoryId, priority, reason);
        }
    }

    private void addRepositoryRef(
            LinkedHashMap<String, RepositoryRef> refsById,
            String repositoryId,
            Integer priority,
            String reason
    ) {
        var normalizedId = normalizeComparable(repositoryId);
        if (!StringUtils.hasText(normalizedId)) {
            return;
        }
        refsById.putIfAbsent(normalizedId, new RepositoryRef(repositoryId.trim(), priority, reason));
    }

    private boolean targetsSystem(OperationalContextRepositorySearchScope scope, String systemId) {
        return systemTargetType(scope.target().type()) && systemId.equals(scope.target().id());
    }

    private boolean systemTargetType(String targetType) {
        var normalized = normalizeComparable(targetType);
        return "system".equals(normalized) || "systems".equals(normalized);
    }

    private boolean gitLabRepository(OperationalContextRepository repository) {
        var provider = normalizeComparable(repository.git().provider());
        return !StringUtils.hasText(provider) || "gitlab".equals(provider);
    }

    private boolean groupMatches(String configuredGroup, OperationalContextRepository repository) {
        var repositoryGroup = normalize(repository.git().group());
        return !StringUtils.hasText(repositoryGroup)
                || normalizeGroupPath(configuredGroup).equals(normalizeGroupPath(repositoryGroup));
    }

    private String projectName(String configuredGroup, OperationalContextRepository repository) {
        var projectPath = firstDefined(
                repository.git().projectPath(),
                firstDefined(repository.git().project(), repository.id())
        );
        return StringUtils.hasText(projectPath)
                ? relativeProjectPath(configuredGroup, projectPath)
                : null;
    }

    private String projectPath(OperationalContextRepository repository) {
        return firstDefined(repository.git().projectPath(), repository.git().project());
    }

    private String relativeProjectPath(String configuredGroup, String rawProjectPath) {
        var projectPath = trimSlashes(rawProjectPath.trim());
        var normalizedGroup = trimSlashes(configuredGroup.trim());
        var prefix = normalizedGroup + "/";
        if (projectPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return projectPath.substring(prefix.length());
        }
        return projectPath;
    }

    private String normalizeGroupPath(String value) {
        return StringUtils.hasText(value)
                ? trimSlashes(value.trim()).toLowerCase(Locale.ROOT)
                : "";
    }

    private String trimSlashes(String value) {
        var start = 0;
        var end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    private List<String> distinct(List<String> values) {
        var distinctValues = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                distinctValues.add(value.trim());
            }
        }
        return List.copyOf(distinctValues);
    }

    private String firstDefined(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replace("_", "-")
                : "";
    }

    private record RepositoryRef(String repositoryId, Integer priority, String reason) {
    }
}
