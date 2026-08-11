package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OperationalContextRepositoryProjectPathResolver {

    private static final Set<OperationalContextEntryType> SYSTEM_REPOSITORY_AND_SCOPE = Set.of(
            OperationalContextEntryType.SYSTEM,
            OperationalContextEntryType.REPOSITORY,
            OperationalContextEntryType.CODE_SEARCH_SCOPE
    );

    private final OperationalContextPort operationalContextPort;

    public List<String> resolveProjectPaths(String configuredGroup, List<String> projectHints) {
        var normalizedHints = normalizedHints(projectHints);
        if (normalizedHints.isEmpty()) {
            return List.of();
        }

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                SYSTEM_REPOSITORY_AND_SCOPE,
                List.of(),
                false
        ));
        var repositoriesById = repositoriesById(catalog.repositories());
        var matchedSystemIds = catalog.systems().stream()
                .filter(system -> matchesSystemId(system, normalizedHints))
                .map(OperationalContextSystem::id)
                .map(this::normalizeComparable)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var resolvedProjectPaths = new LinkedHashSet<String>();

        for (var scope : catalog.codeSearchScopes()) {
            if (!targetsSystem(scope, matchedSystemIds)) {
                continue;
            }
            for (var repositoryId : scopeRepositoryIds(scope)) {
                var repository = repositoriesById.get(normalizeComparable(repositoryId));
                if (repository == null || !groupMatches(configuredGroup, repository)) {
                    continue;
                }
                resolvedProjectPaths.addAll(projectPaths(configuredGroup, repository));
            }
        }

        return List.copyOf(resolvedProjectPaths);
    }

    private LinkedHashSet<String> normalizedHints(List<String> projectHints) {
        var hints = new LinkedHashSet<String>();
        for (var projectHint : projectHints != null ? projectHints : List.<String>of()) {
            var normalized = normalizeComparable(projectHint);
            if (StringUtils.hasText(normalized)) {
                hints.add(normalized);
            }
        }
        return hints;
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

    private boolean matchesSystemId(OperationalContextSystem system, Set<String> normalizedHints) {
        var normalizedSystemId = normalizeComparable(system.id());
        return StringUtils.hasText(normalizedSystemId) && normalizedHints.contains(normalizedSystemId);
    }

    private boolean targetsSystem(
            OperationalContextRepositorySearchScope scope,
            Set<String> matchedSystemIds
    ) {
        return "system".equals(normalizeComparable(scope.target().type()))
                && matchedSystemIds.contains(normalizeComparable(scope.target().id()));
    }

    private List<String> scopeRepositoryIds(OperationalContextRepositorySearchScope scope) {
        return scope.repositories().stream()
                .map(OperationalContextDtos.OperationalContextRepositorySearchRepository::repoId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> projectPaths(String configuredGroup, OperationalContextRepository repository) {
        var projectPaths = new LinkedHashSet<String>();
        addProjectPath(projectPaths, configuredGroup, repository.git().projectPath());
        return List.copyOf(projectPaths);
    }

    private void addProjectPath(LinkedHashSet<String> projectPaths, String configuredGroup, String rawProjectPath) {
        if (!StringUtils.hasText(rawProjectPath)) {
            return;
        }

        var projectPath = relativeProjectPath(configuredGroup, rawProjectPath.trim());
        if (StringUtils.hasText(projectPath)) {
            projectPaths.add(projectPath);
        }
    }

    private boolean groupMatches(String configuredGroup, OperationalContextRepository repository) {
        if (!StringUtils.hasText(configuredGroup)) {
            return true;
        }

        var normalizedConfiguredGroup = normalizeGroupPath(configuredGroup);
        var repositoryGroups = new LinkedHashSet<String>();
        if (StringUtils.hasText(repository.git().group())) {
            repositoryGroups.add(repository.git().group());
        }

        if (repositoryGroups.isEmpty()) {
            return true;
        }

        return repositoryGroups.stream()
                .map(this::normalizeGroupPath)
                .anyMatch(normalizedConfiguredGroup::equals);
    }

    private String relativeProjectPath(String configuredGroup, String rawProjectPath) {
        var projectPath = trimSlashes(rawProjectPath);
        if (!StringUtils.hasText(configuredGroup)) {
            return projectPath;
        }

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

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_")
                : null;
    }
}
