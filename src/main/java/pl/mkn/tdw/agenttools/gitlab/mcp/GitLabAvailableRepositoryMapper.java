package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableCodeSearchRepository;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableCodeSearchScope;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableCodeSearchTarget;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableCodeSearchTraversal;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabAvailableRepository;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    static List<GitLabAvailableCodeSearchScope> codeSearchScopesFromCatalog(
            String sessionGroup,
            OperationalContextCatalog catalog
    ) {
        if (catalog == null || catalog.codeSearchScopes() == null || catalog.codeSearchScopes().isEmpty()) {
            return List.of();
        }

        var repositoriesById = fromCatalog(sessionGroup, catalog).stream()
                .collect(Collectors.toMap(
                        repository -> normalizeId(repository.repositoryId()),
                        Function.identity(),
                        (left, right) -> left
                ));

        return catalog.codeSearchScopes().stream()
                .map(scope -> toCodeSearchScope(scope, repositoriesById))
                .filter(scope -> scope != null && !scope.repositories().isEmpty())
                .sorted(Comparator.comparing(scope -> safeSortKey(scope.scopeId()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static GitLabAvailableRepository toRepository(
            String sessionGroup,
            OperationalContextRepository repository
    ) {
        var rawProjectPath = repository.git().projectPath();
        var projectName = firstNonBlank(
                relativeProjectPath(sessionGroup, rawProjectPath),
                relativeProjectPath(sessionGroup, repository.git().project())
        );
        if (!StringUtils.hasText(projectName)) {
            return null;
        }

        var gitLabPath = fullGitLabPath(sessionGroup, rawProjectPath, projectName);
        var repositoryId = repository.id();
        var name = firstNonBlank(repository.name(), repositoryId, projectName);
        var systems = repository.references().systems();
        var boundedContexts = repository.references().boundedContexts();
        var processes = repository.references().processes();
        var integrations = repository.references().integrations();

        return new GitLabAvailableRepository(
                repositoryId,
                name,
                summary(repository, name, systems, boundedContexts, processes),
                projectName,
                gitLabPath,
                aliases(repository, repositoryId, name, projectName, gitLabPath),
                repository.repositoryType(),
                repository.lifecycleStatus(),
                systems,
                boundedContexts,
                processes,
                integrations,
                repository.references().repositories(),
                packagePrefixes(repository),
                endpointPrefixes(repository),
                modulePaths(repository)
        );
    }

    private static GitLabAvailableCodeSearchScope toCodeSearchScope(
            OperationalContextRepositorySearchScope scope,
            Map<String, GitLabAvailableRepository> repositoriesById
    ) {
        var includedScopeRepositories = sortedIncludedScopeRepositories(scope);
        var repositories = includedScopeRepositories.stream()
                .map(repository -> toCodeSearchRepository(repository, repositoriesById))
                .filter(repository -> repository != null)
                .toList();
        var projectNames = distinctLimited(repositories.stream()
                .flatMap(repository -> repository.projectNames().stream())
                .toList(), MAX_MODULE_PATHS);

        return new GitLabAvailableCodeSearchScope(
                scope.id(),
                firstNonBlank(scope.name(), scope.id()),
                scope.scopeType(),
                scope.lifecycleStatus(),
                new GitLabAvailableCodeSearchTarget(scope.target().type(), scope.target().id()),
                scope.useFor(),
                repositories,
                projectNames,
                distinctLimited(scope.packagePrefixes(), MAX_PACKAGE_PREFIXES),
                distinctLimited(scope.classHints(), MAX_PACKAGE_PREFIXES),
                distinctLimited(scope.endpointHints(), MAX_ENDPOINT_PREFIXES),
                distinctLimited(scope.queueTopicHints(), MAX_ENDPOINT_PREFIXES),
                new GitLabAvailableCodeSearchTraversal(
                        scope.traversal().rules(),
                        scope.traversal().expandWhen()
                )
        );
    }

    private static List<String> combineScopeTargets(List<String> directTargets, List<String> repositoryTargets) {
        var result = new LinkedHashSet<String>();
        directTargets.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(result::add);
        repositoryTargets.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static GitLabAvailableCodeSearchRepository toCodeSearchRepository(
            OperationalContextRepositorySearchRepository scopeRepository,
            Map<String, GitLabAvailableRepository> repositoriesById
    ) {
        var repository = repositoriesById.get(normalizeId(scopeRepository.repoId()));
        if (repository == null) {
            return null;
        }

        return new GitLabAvailableCodeSearchRepository(
                repository.repositoryId(),
                scopeRepository.role(),
                scopeRepository.priority(),
                distinctLimited(List.of(repository.projectName()), MAX_ALIASES),
                scopeRepository.moduleIds(),
                scopeRepository.reason(),
                scopeRepository.readFor()
        );
    }

    private static List<OperationalContextRepositorySearchRepository> sortedIncludedScopeRepositories(
            OperationalContextRepositorySearchScope scope
    ) {
        return scope.repositories().stream()
                .sorted(Comparator.comparing(
                        repository -> repository.priority() != null ? repository.priority() : Integer.MAX_VALUE
                ))
                .toList();
    }

    private static boolean isGitLabRepository(OperationalContextRepository repository) {
        var provider = repository.git().provider();
        return !StringUtils.hasText(provider) || "gitlab".equalsIgnoreCase(provider.trim());
    }

    private static boolean groupMatches(String sessionGroup, OperationalContextRepository repository) {
        if (!StringUtils.hasText(sessionGroup)) {
            return true;
        }

        var repositoryGroup = repository.git().group();
        if (StringUtils.hasText(repositoryGroup)) {
            return GitLabPathUtils.isSameOrNestedPath(sessionGroup, repositoryGroup);
        }

        var projectPath = repository.git().projectPath();
        if (!StringUtils.hasText(projectPath)) {
            return true;
        }

        return GitLabPathUtils.isSameOrNestedPath(sessionGroup, projectPath);
    }

    private static List<String> aliases(
            OperationalContextRepository repository,
            String repositoryId,
            String name,
            String projectName,
            String gitLabPath
    ) {
        var aliases = new ArrayList<String>();
        aliases.add(repositoryId);
        aliases.add(name);
        aliases.add(repository.git().project());
        aliases.add(projectName);
        aliases.add(gitLabPath);
        aliases.addAll(repository.git().aliases());
        aliases.addAll(repository.matchSignals().exact().projectNames());
        aliases.addAll(repository.matchSignals().exact().projectPaths());
        return distinctLimited(aliases, MAX_ALIASES);
    }

    private static List<String> packagePrefixes(OperationalContextRepository repository) {
        return distinctLimited(repository.packagePrefixSignals(), MAX_PACKAGE_PREFIXES);
    }

    private static List<String> endpointPrefixes(OperationalContextRepository repository) {
        return distinctLimited(repository.endpointPrefixSignals(), MAX_ENDPOINT_PREFIXES);
    }

    private static List<String> modulePaths(OperationalContextRepository repository) {
        var values = new ArrayList<String>();
        values.addAll(repository.sourceLayout().modulePaths());
        for (var module : repository.modules()) {
            values.add(module.effectiveId());
        }
        return distinctLimited(values, MAX_MODULE_PATHS);
    }

    private static String summary(
            OperationalContextRepository repository,
            String name,
            List<String> systems,
            List<String> boundedContexts,
            List<String> processes
    ) {
        var parts = new ArrayList<String>();
        var explicitSummary = firstNonBlank(
                repository.summary(),
                repository.purpose()
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

        return GitLabPathUtils.relativeProjectPath(sessionGroup, rawProjectPath);
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

    private static String normalizeId(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
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
