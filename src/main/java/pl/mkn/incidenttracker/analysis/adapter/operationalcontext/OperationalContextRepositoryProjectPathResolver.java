package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
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

    private static final Set<OperationalContextEntryType> REPOSITORY_ONLY =
            Set.of(OperationalContextEntryType.REPOSITORY);

    private final OperationalContextPort operationalContextPort;

    public List<String> resolveProjectPaths(String configuredGroup, List<String> projectHints) {
        var normalizedHints = normalizedHints(projectHints);
        if (normalizedHints.isEmpty()) {
            return List.of();
        }

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                REPOSITORY_ONLY,
                List.of(),
                false
        ));
        var candidates = new LinkedHashMap<String, ProjectPathCandidate>();

        for (var repository : catalog.repositories()) {
            var groupMatches = groupMatches(configuredGroup, repository);
            var projectPaths = projectPaths(configuredGroup, repository);
            if (!groupMatches) {
                continue;
            }

            var score = matchScore(repository, normalizedHints);
            if (score <= 0) {
                continue;
            }

            for (var projectPath : projectPaths) {
                candidates.merge(
                        projectPath,
                        new ProjectPathCandidate(projectPath, score),
                        (current, replacement) -> replacement.score() > current.score() ? replacement : current
                );
            }
        }

        return candidates.values().stream()
                .sorted(Comparator
                        .comparingInt(ProjectPathCandidate::score)
                        .reversed()
                        .thenComparing(ProjectPathCandidate::projectPath))
                .map(ProjectPathCandidate::projectPath)
                .toList();
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

    private List<String> projectPaths(String configuredGroup, Map<String, Object> repository) {
        var projectPaths = new LinkedHashSet<String>();
        for (var projectPath : textList(repository, "gitLab.projectPath")) {
            addProjectPath(projectPaths, configuredGroup, projectPath);
        }
        addProjectPath(projectPaths, configuredGroup, text(repository, "project"));
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

    private int matchScore(Map<String, Object> repository, Set<String> normalizedHints) {
        var score = 0;
        score += scoreValues(normalizedHints, textList(repository, "project"), 60, 35);
        score += scoreValues(normalizedHints, textList(repository, "gitLab.projectPath"), 60, 35);
        score += scoreValues(normalizedHints, textList(repository, "runtimeMappings.projectNames"), 55, 35);
        score += scoreValues(normalizedHints, textList(repository, "signals.projectNames"), 55, 35);
        score += scoreValues(normalizedHints, textList(repository, "runtimeMappings.serviceNames"), 35, 20);
        score += scoreValues(normalizedHints, textList(repository, "signals.serviceNames"), 35, 20);
        score += scoreValues(normalizedHints, textList(repository, "runtimeMappings.containerNames"), 35, 20);
        score += scoreValues(normalizedHints, textList(repository, "signals.containerNames"), 35, 20);
        score += scoreValues(normalizedHints, textList(repository, "runtimeMappings.applicationNames"), 30, 16);
        return score;
    }

    private int scoreValues(
            Set<String> normalizedHints,
            List<String> candidateValues,
            int exactPoints,
            int containsPoints
    ) {
        var score = 0;
        for (var candidateValue : candidateValues) {
            var normalizedCandidate = normalizeComparable(candidateValue);
            if (!StringUtils.hasText(normalizedCandidate)) {
                continue;
            }

            for (var normalizedHint : normalizedHints) {
                if (normalizedHint.equals(normalizedCandidate)
                        || normalizedCandidate.endsWith("/" + normalizedHint)) {
                    score += exactPoints;
                } else if (normalizedCandidate.contains(normalizedHint)) {
                    score += containsPoints;
                }
            }
        }
        return score;
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

    private record ProjectPathCandidate(
            String projectPath,
            int score
    ) {
    }
}
