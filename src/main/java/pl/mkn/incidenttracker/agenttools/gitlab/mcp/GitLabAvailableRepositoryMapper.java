package pl.mkn.incidenttracker.agenttools.gitlab.mcp;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

final class GitLabAvailableRepositoryMapper {

    private static final int MAX_ALIASES = 20;
    private static final int MAX_PACKAGE_PREFIXES = 40;
    private static final int MAX_ENDPOINT_PREFIXES = 40;
    private static final int MAX_MODULE_PATHS = 40;
    private static final int MAX_SUMMARY_CHARACTERS = 700;
    private static final int SUMMARY_LIST_PREVIEW_SIZE = 5;

    private GitLabAvailableRepositoryMapper() {
    }

    static List<GitLabAvailableRepository> fromCatalog(
            String sessionGroup,
            OperationalContextCatalog catalog
    ) {
        if (catalog == null || catalog.repositories() == null) {
            return List.of();
        }

        return catalog.repositories().stream()
                .filter(GitLabAvailableRepositoryMapper::isGitLabRepository)
                .filter(repository -> groupMatches(sessionGroup, repository))
                .map(repository -> toRepository(sessionGroup, repository))
                .filter(repository -> repository != null)
                .sorted(Comparator.comparing(
                        repository -> safeSortKey(repository.projectName()),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private static GitLabAvailableRepository toRepository(
            String sessionGroup,
            Map<String, Object> repository
    ) {
        var rawProjectPath = first(textList(repository, "git.projectPath"));
        var projectName = firstNonBlank(
                relativeProjectPath(sessionGroup, rawProjectPath),
                relativeProjectPath(sessionGroup, text(repository, "git.project"))
        );
        if (!StringUtils.hasText(projectName)) {
            return null;
        }

        var gitLabPath = fullGitLabPath(sessionGroup, rawProjectPath, projectName);
        var repositoryId = text(repository, "id");
        var name = firstNonBlank(text(repository, "name"), repositoryId, projectName);
        var systems = textList(repository, "references.systems");
        var runtimeComponents = textList(repository, "references.runtimeComponents");
        var boundedContexts = textList(repository, "references.boundedContexts");
        var processes = textList(repository, "references.processes");
        var integrations = textList(repository, "references.integrations");

        return new GitLabAvailableRepository(
                repositoryId,
                name,
                summary(repository, name, systems, boundedContexts, processes),
                projectName,
                gitLabPath,
                aliases(repository, repositoryId, name, projectName, gitLabPath),
                text(repository, "repositoryType"),
                text(repository, "lifecycleStatus"),
                systems,
                runtimeComponents,
                boundedContexts,
                processes,
                integrations,
                textList(repository, "references.repositories"),
                packagePrefixes(repository),
                endpointPrefixes(repository),
                modulePaths(repository)
        );
    }

    private static boolean isGitLabRepository(Map<String, Object> repository) {
        var provider = text(repository, "git.provider");
        return !StringUtils.hasText(provider) || "gitlab".equalsIgnoreCase(provider.trim());
    }

    private static boolean groupMatches(String sessionGroup, Map<String, Object> repository) {
        if (!StringUtils.hasText(sessionGroup)) {
            return true;
        }

        var normalizedSessionGroup = normalizePath(sessionGroup);
        var repositoryGroups = textList(repository, "git.group");
        if (!repositoryGroups.isEmpty()) {
            return repositoryGroups.stream()
                    .map(GitLabAvailableRepositoryMapper::normalizePath)
                    .anyMatch(normalizedSessionGroup::equals);
        }

        var projectPaths = textList(repository, "git.projectPath");
        return projectPaths.isEmpty()
                || projectPaths.stream()
                .map(GitLabAvailableRepositoryMapper::normalizePath)
                .anyMatch(path -> path.equals(normalizedSessionGroup) || path.startsWith(normalizedSessionGroup + "/"));
    }

    private static List<String> aliases(
            Map<String, Object> repository,
            String repositoryId,
            String name,
            String projectName,
            String gitLabPath
    ) {
        var aliases = new ArrayList<String>();
        aliases.add(repositoryId);
        aliases.add(name);
        aliases.add(text(repository, "git.project"));
        aliases.add(projectName);
        aliases.add(gitLabPath);
        aliases.addAll(textList(repository, "git.aliases"));
        aliases.addAll(textList(repository, "matchSignals.exact.projectNames"));
        aliases.addAll(textList(repository, "matchSignals.exact.projectPaths"));
        return distinctLimited(aliases, MAX_ALIASES);
    }

    private static List<String> packagePrefixes(Map<String, Object> repository) {
        var values = new ArrayList<String>();
        values.addAll(textList(repository, "matchSignals.strong.packagePrefixes"));
        values.addAll(textList(repository, "matchSignals.medium.packagePrefixes"));
        for (var module : mapList(repository, "modules")) {
            values.addAll(textList(module, "matchSignals.strong.packagePrefixes"));
            values.addAll(textList(module, "matchSignals.medium.packagePrefixes"));
        }
        return distinctLimited(values, MAX_PACKAGE_PREFIXES);
    }

    private static List<String> endpointPrefixes(Map<String, Object> repository) {
        var values = new ArrayList<String>();
        values.addAll(textList(repository, "matchSignals.strong.endpointPrefixes"));
        values.addAll(textList(repository, "matchSignals.medium.endpointPrefixes"));
        for (var module : mapList(repository, "modules")) {
            values.addAll(textList(module, "matchSignals.strong.endpointPrefixes"));
            values.addAll(textList(module, "matchSignals.medium.endpointPrefixes"));
        }
        return distinctLimited(values, MAX_ENDPOINT_PREFIXES);
    }

    private static List<String> modulePaths(Map<String, Object> repository) {
        var values = new ArrayList<String>();
        values.addAll(textList(repository, "sourceLayout.modulePaths"));
        for (var module : mapList(repository, "modules")) {
            values.addAll(textList(module, "id"));
        }
        return distinctLimited(values, MAX_MODULE_PATHS);
    }

    private static String summary(
            Map<String, Object> repository,
            String name,
            List<String> systems,
            List<String> boundedContexts,
            List<String> processes
    ) {
        var parts = new ArrayList<String>();
        var explicitSummary = firstNonBlank(
                text(repository, "summary"),
                text(repository, "description"),
                text(repository, "purpose")
        );
        if (StringUtils.hasText(explicitSummary)) {
            parts.add(explicitSummary);
        } else if (StringUtils.hasText(name)) {
            parts.add(name + " repository registered in operational context.");
        }

        addSummaryList(parts, "Systems", systems);
        addSummaryList(parts, "Bounded contexts", boundedContexts);
        addSummaryList(parts, "Processes", processes);

        return abbreviate(String.join(" ", parts), MAX_SUMMARY_CHARACTERS);
    }

    private static void addSummaryList(List<String> parts, String label, List<String> values) {
        var limited = distinctLimited(values, SUMMARY_LIST_PREVIEW_SIZE);
        if (!limited.isEmpty()) {
            parts.add(label + ": " + String.join(", ", limited) + ".");
        }
    }

    private static String fullGitLabPath(String sessionGroup, String rawProjectPath, String projectName) {
        if (StringUtils.hasText(rawProjectPath)) {
            var normalizedPath = trimSlashes(rawProjectPath.trim());
            if (StringUtils.hasText(normalizedPath)) {
                return normalizedPath;
            }
        }

        if (StringUtils.hasText(sessionGroup)) {
            return trimSlashes(sessionGroup.trim()) + "/" + trimSlashes(projectName);
        }

        return projectName;
    }

    private static String relativeProjectPath(String sessionGroup, String rawProjectPath) {
        if (!StringUtils.hasText(rawProjectPath)) {
            return null;
        }

        var projectPath = trimSlashes(rawProjectPath.trim());
        if (!StringUtils.hasText(sessionGroup)) {
            return projectPath;
        }

        var normalizedGroup = trimSlashes(sessionGroup.trim());
        var prefix = normalizedGroup + "/";
        if (projectPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return projectPath.substring(prefix.length());
        }

        return projectPath;
    }

    private static List<String> distinctLimited(List<String> values, int limit) {
        var distinct = new LinkedHashSet<String>();
        for (var value : values != null ? values : List.<String>of()) {
            if (!StringUtils.hasText(value)) {
                continue;
            }

            distinct.add(value.trim());
            if (limit > 0 && distinct.size() >= limit) {
                break;
            }
        }
        return List.copyOf(distinct);
    }

    private static String first(List<String> values) {
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String abbreviate(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxCharacters
                ? normalized.substring(0, maxCharacters) + "..."
                : normalized;
    }

    private static String normalizePath(String value) {
        return StringUtils.hasText(value)
                ? trimSlashes(value.trim()).toLowerCase(Locale.ROOT)
                : "";
    }

    private static String trimSlashes(String value) {
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

    private static String safeSortKey(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
