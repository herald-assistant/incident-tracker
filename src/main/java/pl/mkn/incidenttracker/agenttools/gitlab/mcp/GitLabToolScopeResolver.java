package pl.mkn.incidenttracker.agenttools.gitlab.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabToolScope;
import pl.mkn.incidenttracker.common.GitLabPathUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextQuery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GitLabToolScopeResolver {

    private static final Set<OperationalContextEntryType> GITLAB_SCOPE_ENTRY_TYPES =
            Set.of(OperationalContextEntryType.SYSTEM, OperationalContextEntryType.REPOSITORY);

    private final GitLabProperties gitLabProperties;
    private final OperationalContextPort operationalContextPort;

    GitLabToolScopeResolver(
            GitLabProperties gitLabProperties,
            OperationalContextPort operationalContextPort
    ) {
        this.gitLabProperties = gitLabProperties != null ? gitLabProperties : new GitLabProperties();
        this.operationalContextPort = operationalContextPort;
    }

    GitLabToolScope resolve(
            String branchRef,
            String projectName,
            String applicationName,
            ToolContext toolContext
    ) {
        var branch = requiredBranch(branchRef);
        var catalog = loadCatalog();
        var group = resolveGroup(projectName, applicationName, catalog);
        return GitLabToolScope.fromResolvedScope(group, branch, trimToNull(applicationName), toolContext);
    }

    private String resolveGroup(
            String projectName,
            String applicationName,
            OperationalContextCatalog catalog
    ) {
        var configuredGroup = configuredGroup();
        var repository = findRepository(projectName, catalog.repositories());
        if (repository != null) {
            var repositoryGroup = trimToNull(repository.git().group());
            if (StringUtils.hasText(configuredGroup)
                    && StringUtils.hasText(repositoryGroup)
                    && !GitLabPathUtils.isSameOrNestedPath(configuredGroup, repositoryGroup)) {
                throw new IllegalArgumentException(
                        "GitLab project '%s' belongs to group '%s', not configured group '%s'."
                                .formatted(projectName, repositoryGroup, configuredGroup)
                );
            }
            return StringUtils.hasText(configuredGroup) ? configuredGroup : repositoryGroup;
        }

        var applicationGroups = applicationRepositoryGroups(applicationName, catalog);
        if (!applicationGroups.isEmpty()) {
            if (StringUtils.hasText(configuredGroup)) {
                var configuredGroupAllowed = applicationGroups.stream()
                        .anyMatch(group -> GitLabPathUtils.isSameOrNestedPath(configuredGroup, group));
                if (!configuredGroupAllowed) {
                    throw new IllegalArgumentException(
                            "Application '%s' repositories do not belong to configured GitLab group '%s'."
                                    .formatted(applicationName, configuredGroup)
                    );
                }
                return configuredGroup;
            }
            if (applicationGroups.size() == 1) {
                return applicationGroups.iterator().next();
            }
            throw new IllegalArgumentException(
                    "Application '%s' maps to multiple GitLab groups; configure analysis.gitlab.group."
                            .formatted(applicationName)
            );
        }

        if (StringUtils.hasText(configuredGroup)) {
            return configuredGroup;
        }

        throw new IllegalStateException(
                "GitLab group could not be resolved from operational context or analysis.gitlab.group."
        );
    }

    private OperationalContextCatalog loadCatalog() {
        if (operationalContextPort == null) {
            return OperationalContextCatalog.empty();
        }
        return operationalContextPort.loadContext(new OperationalContextQuery(
                GITLAB_SCOPE_ENTRY_TYPES,
                List.of(),
                false
        ));
    }

    private OperationalContextRepository findRepository(
            String projectName,
            List<OperationalContextRepository> repositories
    ) {
        var normalizedProjectName = normalizeComparable(projectName);
        if (!StringUtils.hasText(normalizedProjectName)) {
            return null;
        }
        for (var repository : repositories != null ? repositories : List.<OperationalContextRepository>of()) {
            var candidates = new LinkedHashSet<String>();
            add(candidates, repository.id());
            add(candidates, repository.name());
            add(candidates, repository.git().project());
            add(candidates, repository.git().projectPath());
            add(candidates, relativeProjectPath(configuredGroup(), repository.git().projectPath()));
            addAll(candidates, repository.git().aliases());
            addAll(candidates, repository.aliases());
            if (candidates.stream().map(this::normalizeComparable).anyMatch(normalizedProjectName::equals)) {
                return repository;
            }
        }
        return null;
    }

    private LinkedHashSet<String> applicationRepositoryGroups(
            String applicationName,
            OperationalContextCatalog catalog
    ) {
        var normalizedApplicationName = normalizeComparable(applicationName);
        var matchingSystemIds = new LinkedHashSet<String>();
        if (!StringUtils.hasText(normalizedApplicationName)) {
            return new LinkedHashSet<>();
        }

        for (var system : catalog.systems()) {
            if (systemMatches(system, normalizedApplicationName)) {
                add(matchingSystemIds, normalizeComparable(system.id()));
            }
        }

        var groups = new LinkedHashSet<String>();
        for (var repository : catalog.repositories()) {
            var repositorySystems = repository.references().systems();
            var repositoryMatchesSystem = repositorySystems.stream()
                    .map(this::normalizeComparable)
                    .anyMatch(matchingSystemIds::contains);
            if (repositoryMatchesSystem || repositorySystems.stream()
                    .map(this::normalizeComparable)
                    .anyMatch(normalizedApplicationName::equals)) {
                add(groups, repository.git().group());
            }
        }
        return groups;
    }

    private boolean systemMatches(OperationalContextSystem system, String normalizedApplicationName) {
        var values = new LinkedHashSet<String>();
        add(values, system.id());
        add(values, system.name());
        add(values, system.shortName());
        addAll(values, system.aliases());
        addAll(values, system.genericSignals());
        return values.stream()
                .map(this::normalizeComparable)
                .anyMatch(normalizedApplicationName::equals);
    }

    private String configuredGroup() {
        return trimToNull(gitLabProperties.getGroup());
    }

    private String requiredBranch(String branchRef) {
        var normalizedBranch = trimToNull(branchRef);
        if (!StringUtils.hasText(normalizedBranch)) {
            throw new IllegalArgumentException("branchRef must be provided explicitly for GitLab tools.");
        }
        return normalizedBranch;
    }

    private String relativeProjectPath(String configuredGroup, String rawProjectPath) {
        return GitLabPathUtils.relativeProjectPath(configuredGroup, rawProjectPath);
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_")
                : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void add(LinkedHashSet<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private void addAll(LinkedHashSet<String> values, List<String> source) {
        for (var value : source != null ? source : List.<String>of()) {
            add(values, value);
        }
    }
}
