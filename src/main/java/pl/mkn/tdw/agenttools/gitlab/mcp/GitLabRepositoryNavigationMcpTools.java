package pl.mkn.tdw.agenttools.gitlab.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeExplorer;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeSlice;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_FILES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_BRANCHES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_TREE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.SEARCH_REPOSITORY_FILES;

/** General GitLab navigation tools. Feature policy decides which of them are available. */
@Component
@RequiredArgsConstructor
public class GitLabRepositoryNavigationMcpTools {

    private static final int MAX_LIST_RESULTS = 200;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_SEARCH_SCAN_FILES = 20;
    private static final Pattern CURSOR = Pattern.compile("[A-Za-z0-9_-]{1,2048}");

    private final GitLabRepositoryPort repositoryPort;
    private final GitLabRepositoryTreeExplorer treeExplorer;
    private final GitLabRepositoryBranchService branchService;
    private final GitLabProperties properties;
    private final OperationalContextPort operationalContextPort;

    @Tool(name = LIST_REPOSITORY_BRANCHES, description = """
            List up to 100 GitLab branches, including the default branch marker, for a project
            inside the configured main group. Use this to discover a branch before inspecting
            another project; never guess its branch. The operator-selected project remains
            bound to the operator-selected branch for all content tools.
            """)
    public BranchesResult listRepositoryBranches(
            @ToolParam(description = "Project path relative to the configured GitLab group, including subgroups.") String projectName,
            @ToolParam(description = "Optional branch-name filter.", required = false) String search,
            @ToolParam(description = "Krótki powód sprawdzenia gałęzi po polsku.") String reason,
            ToolContext toolContext
    ) {
        requireReason(reason);
        if (search != null && (search.length() > 120 || search.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Branch search must be at most 120 printable characters.");
        }
        var values = toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext() : Map.<String, Object>of();
        var bound = values.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        var projectPath = bound instanceof GitLabRepositoryToolScope scope
                ? scope.projectPath(projectName)
                : GitLabRepositoryToolScope.projectPathInGroup(properties.getGroup(), projectName);
        var page = branchService.listBranches(projectPath, search);
        return new BranchesResult(projectPath, page.branches(), page.truncated());
    }

    @Tool(name = LIST_REPOSITORY_TREE, description = """
            Show up to four levels of directory and file paths beneath path in a GitLab project.
            projectName is relative to the configured main group and may include subgroups.
            Use the operator-selected branch for the selected project. A different project in the
            same group can be inspected when it is needed to verify a cross-repository relationship.
            Results contain names only; read a file before citing its contents.
            """)
    public TreeResult listRepositoryTree(
            @ToolParam(description = "Project path relative to the configured GitLab group, including subgroups.") String projectName,
            @ToolParam(description = "GitLab branch. Use the operator-selected branch for the selected project.") String branchRef,
            @ToolParam(description = "Relative directory path; empty string starts at repository root.", required = false) String path,
            @ToolParam(description = "Optional continuation cursor for the same path.", required = false) String cursor,
            @ToolParam(description = "Krótki powód przeglądania drzewa po polsku.") String reason,
            ToolContext toolContext
    ) {
        requireReason(reason);
        var target = target(projectName, branchRef, toolContext);
        var safePath = safePath(path, true);
        var safeCursor = safeCursor(cursor);
        var tree = treeExplorer.explore(target.group(), target.projectName(), target.commitId(), safePath, safeCursor);
        return new TreeResult(target.projectPath(), target.branch(), target.commitId(), tree.path(),
                tree.depth(), tree.entries(), tree.continuations(), tree.truncated());
    }

    @Tool(name = LIST_REPOSITORY_FILES, description = """
            List bounded file paths in one GitLab project and branch.
            projectName is relative to the configured main group and may include subgroups.
            Use nextCursor to continue a partial page. Paths are navigation, not source citations.
            """)
    public FilePathsResult listRepositoryFiles(
            @ToolParam(description = "Project path relative to the configured GitLab group, including subgroups.") String projectName,
            @ToolParam(description = "GitLab branch. Use the operator-selected branch for the selected project.") String branchRef,
            @ToolParam(description = "Optional relative directory prefix.", required = false) String pathPrefix,
            @ToolParam(description = "Optional nextCursor returned for this project, branch and prefix.", required = false) String afterPath,
            @ToolParam(description = "Krótki powód listowania po polsku.") String reason,
            ToolContext toolContext
    ) {
        requireReason(reason);
        var target = target(projectName, branchRef, toolContext);
        var prefix = safePath(pathPrefix, true);
        var page = repositoryPort.listRepositoryFilesPage(target.group(), target.projectName(),
                target.commitId(), prefix, safeCursor(afterPath), MAX_LIST_RESULTS);
        var paths = page.files().stream()
                .filter(file -> matches(file, target) && withinPrefix(file.filePath(), prefix)
                        && GitLabVerifiedRepositoryFileReader.isSafePath(file.filePath(), false))
                .map(GitLabRepositoryFile::filePath).distinct().toList();
        return new FilePathsResult(target.projectPath(), target.branch(), target.commitId(), paths,
                page.nextCursor() != null, page.nextCursor());
    }

    @Tool(name = SEARCH_REPOSITORY_FILES, description = """
            Search file contents for a short literal in one GitLab project and branch. Results are
            matching paths only and must be followed by a file read before citing contents.
            projectName is relative to the configured main group and may include subgroups.
            Use this for identifiers, imports, dependencies and integration references.
            When GitLab blob search has no verified hits, a bounded file scan can continue
            using nextCursor and the same pathPrefix.
            """)
    public FilePathsResult searchRepositoryFiles(
            @ToolParam(description = "Project path relative to the configured GitLab group, including subgroups.") String projectName,
            @ToolParam(description = "GitLab branch. Use the operator-selected branch for the selected project.") String branchRef,
            @ToolParam(description = "Literal search term, 2-120 printable characters.") String query,
            @ToolParam(description = "Optional relative directory prefix.", required = false) String pathPrefix,
            @ToolParam(description = "Optional nextCursor from a previous partial search with the same prefix.", required = false) String afterPath,
            @ToolParam(description = "Krótki powód wyszukiwania po polsku.") String reason,
            ToolContext toolContext
    ) {
        requireReason(reason);
        if (query == null || query.length() < 2 || query.length() > 120
                || query.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Search query must contain 2-120 printable characters.");
        }
        var target = target(projectName, branchRef, toolContext);
        var prefix = safePath(pathPrefix, true);
        var cursor = safeCursor(afterPath);
        if (!cursor.isEmpty()) {
            return scanPinnedFiles(target, prefix, cursor, query);
        }
        // GitLab's blob search accepts a branch/tag ref, not a commit. Verify every hit
        // against the session-pinned commit before exposing the path to the model.
        try {
            var candidates = repositoryPort.searchRepositoryFilesByContent(
                    target.group(), target.projectName(), target.branch(), List.of(query), MAX_SEARCH_RESULTS);
            var paths = candidates.stream()
                    .filter(candidate -> matches(candidate, target) && withinPrefix(candidate.filePath(), prefix)
                            && GitLabVerifiedRepositoryFileReader.isSafePath(candidate.filePath(), false))
                    .map(GitLabRepositoryFileCandidate::filePath).distinct().limit(MAX_SEARCH_RESULTS).toList();
            var verifiedPaths = paths.stream().filter(path -> matchesPinnedContent(target, path, query)).toList();
            if (!verifiedPaths.isEmpty()) {
                return new FilePathsResult(target.projectPath(), target.branch(), target.commitId(), verifiedPaths,
                        candidates.size() >= MAX_SEARCH_RESULTS, null);
            }
        } catch (RuntimeException ignored) {
            // Some GitLab installations do not provide blob search; use a bounded pinned scan.
        }
        return scanPinnedFiles(target, prefix, "", query);
    }

    private GitLabRepositoryToolScope.Target target(String projectName, String branchRef, ToolContext context) {
        var values = context != null && context.getContext() != null ? context.getContext() : Map.<String, Object>of();
        var bound = values.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        if (bound instanceof GitLabRepositoryToolScope scope) {
            return scope.resolve(projectName, branchRef, repositoryPort);
        }
        var resolved = new GitLabToolScopeResolver(properties, operationalContextPort)
                .resolve(branchRef, projectName, List.of(), context);
        var group = resolved.group();
        var relative = projectName != null && projectName.startsWith(group + "/")
                ? projectName.substring(group.length() + 1) : projectName;
        if (!validProject(relative)) {
            throw new IllegalArgumentException("Project path must be inside the configured GitLab group.");
        }
        var revision = repositoryPort.resolveRevision(group, relative, resolved.branch());
        if (revision == null || !group.equals(revision.group()) || !relative.equals(revision.projectName())
                || !resolved.branch().equals(revision.ref()) || revision.commitId() == null
                || !revision.commitId().matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")) {
            throw new IllegalStateException("GitLab branch could not be pinned to a commit.");
        }
        return new GitLabRepositoryToolScope.Target(group, relative, resolved.branch(), revision.commitId());
    }

    private boolean validProject(String path) {
        return path != null && path.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}")
                && !path.endsWith("/") && !path.contains("//") && !path.contains("..");
    }

    private String safePath(String path, boolean allowRoot) {
        if (allowRoot && (path == null || path.isBlank())) {
            return "";
        }
        if (!GitLabVerifiedRepositoryFileReader.isSafePath(path, false)) {
            throw new IllegalArgumentException("A safe relative GitLab path is required.");
        }
        return path;
    }

    private String safeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return "";
        }
        if (!CURSOR.matcher(cursor).matches()) {
            throw new IllegalArgumentException("A valid opaque GitLab cursor is required.");
        }
        return cursor;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A short reason is required.");
        }
    }

    private boolean withinPrefix(String path, String prefix) {
        return prefix.isEmpty() || path != null && (path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private boolean matches(GitLabRepositoryFile file, GitLabRepositoryToolScope.Target target) {
        return file != null && target.group().equals(file.group())
                && target.projectName().equals(file.projectName()) && target.commitId().equals(file.branch());
    }

    private boolean matches(GitLabRepositoryFileCandidate file, GitLabRepositoryToolScope.Target target) {
        return file != null && target.group().equals(file.group())
                && target.projectName().equals(file.projectName()) && target.branch().equals(file.branch());
    }

    private boolean matchesPinnedContent(GitLabRepositoryToolScope.Target target, String path, String query) {
        try {
            var file = GitLabVerifiedRepositoryFileReader.read(
                    repositoryPort, target.group(), target.projectName(), target.commitId(), path,
                    GitLabVerifiedRepositoryFileReader.MAX_FILE_BYTES);
            return file.content().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private FilePathsResult scanPinnedFiles(
            GitLabRepositoryToolScope.Target target, String prefix, String cursor, String query
    ) {
        var page = repositoryPort.listRepositoryFilesPage(target.group(), target.projectName(),
                target.commitId(), prefix, cursor, MAX_SEARCH_SCAN_FILES);
        var paths = page.files().stream()
                .filter(file -> matches(file, target) && withinPrefix(file.filePath(), prefix)
                        && GitLabVerifiedRepositoryFileReader.isSafePath(file.filePath(), false))
                .map(GitLabRepositoryFile::filePath).distinct()
                .filter(path -> matchesPinnedContent(target, path, query)).toList();
        return new FilePathsResult(target.projectPath(), target.branch(), target.commitId(), paths,
                page.nextCursor() != null, page.nextCursor());
    }

    public record FilePathsResult(String projectPath, String branch, String commitId,
                                  List<String> paths, boolean truncated, String nextCursor) {
    }

    public record BranchesResult(String projectPath, List<GitLabRepositoryBranchService.Branch> branches,
                                 boolean truncated) {
    }

    public record TreeResult(String projectPath, String branch, String commitId,
                             String path, int depth, List<GitLabRepositoryTreeSlice.Entry> entries,
                             List<GitLabRepositoryTreeSlice.Continuation> continuations, boolean truncated) {
    }
}
