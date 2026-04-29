package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextMaps.textList;

@Component
@RequiredArgsConstructor
public class OperationalContextRepositoryProjectPathResolver {

    private static final Set<OperationalContextEntryType> SYSTEM_AND_REPOSITORY =
            Set.of(OperationalContextEntryType.SYSTEM, OperationalContextEntryType.REPOSITORY);

    private final OperationalContextPort operationalContextPort;

    public List<String> resolveProjectPaths(String configuredGroup, List<String> projectHints) {
        var normalizedHints = normalizedHints(projectHints);
        if (normalizedHints.isEmpty()) {
            return List.of();
        }

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                SYSTEM_AND_REPOSITORY,
                List.of(),
                false
        ));
        var repositoriesById = repositoriesById(catalog.repositories());
        var resolvedProjectPaths = new LinkedHashSet<String>();

        for (var system : catalog.systems()) {
            if (!matchesSystemId(system, normalizedHints)) {
                continue;
            }

            for (var repositoryId : systemRepositoryIds(system)) {
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

    private Map<String, Map<String, Object>> repositoriesById(List<Map<String, Object>> repositories) {
        var repositoriesById = new LinkedHashMap<String, Map<String, Object>>();
        for (var repository : repositories) {
            var normalizedId = normalizeComparable(text(repository, "id"));
            if (StringUtils.hasText(normalizedId)) {
                repositoriesById.putIfAbsent(normalizedId, repository);
            }
        }
        return repositoriesById;
    }

    private boolean matchesSystemId(Map<String, Object> system, Set<String> normalizedHints) {
        var normalizedSystemId = normalizeComparable(text(system, "id"));
        return StringUtils.hasText(normalizedSystemId) && normalizedHints.contains(normalizedSystemId);
    }

    private List<String> systemRepositoryIds(Map<String, Object> system) {
        var repositoryIds = new LinkedHashSet<String>();
        repositoryIds.addAll(textList(system, "repos"));
        repositoryIds.addAll(textList(system, "repositories.primary"));
        repositoryIds.addAll(textList(system, "repositories.secondary"));
        repositoryIds.addAll(textList(system, "repositories.backendModules"));
        repositoryIds.addAll(textList(system, "repositories.frontendModules"));
        return List.copyOf(repositoryIds);
    }

    private List<String> projectPaths(String configuredGroup, Map<String, Object> repository) {
        var projectPaths = new LinkedHashSet<String>();
        for (var projectPath : textList(repository, "gitLab.projectPath")) {
            addProjectPath(projectPaths, configuredGroup, projectPath);
        }
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

    private boolean groupMatches(String configuredGroup, Map<String, Object> repository) {
        if (!StringUtils.hasText(configuredGroup)) {
            return true;
        }

        var normalizedConfiguredGroup = normalizeGroupPath(configuredGroup);
        var repositoryGroups = new LinkedHashSet<String>();
        repositoryGroups.addAll(textList(repository, "gitLab.groupPath"));
        repositoryGroups.addAll(textList(repository, "group"));

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
