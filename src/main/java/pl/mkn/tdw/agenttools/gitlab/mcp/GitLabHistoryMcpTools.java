package pl.mkn.tdw.agenttools.gitlab.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.integrations.gitlab.GitLabCommitHistoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabHistoryCommit;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextNavigationPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GitLabHistoryMcpTools {
    private static final ObjectMapper CURSORS = new ObjectMapper().findAndRegisterModules();
    private static final int MAX_PATHS = 10;
    private static final int PATCH_CHARACTERS = 20_000;
    private final GitLabCommitHistoryPort history;
    private final GitLabRepositoryRevisionPort revisions;
    private final GitLabSettingsPort settings;
    private final OperationalContextPort operationalContext;
    private final OperationalContextNavigationPort navigation;

    @Tool(name = GitLabToolNames.LIST_REPOSITORY_COMMITS, description = "List commits reachable from any requested repository ref, defaulting to the session ref. "
            + "filePaths is a union of up to 10 paths; matchedPaths identifies requested paths, not all changed files. "
            + "Continue with nextCursor and identical filters. Committer metadata is optional and is not authorship. "
            + "Commit dates are not deployment dates or proof of a failure cause. History across renames depends on GitLab capabilities.")
    public CommitResult listRepositoryCommits(
            @ToolParam(description = "Exact project path relative to the configured group.") String projectName,
            @ToolParam(required = false, description = "Branch, tag or commit to explore; defaults to the session ref.") String ref,
            @ToolParam(required = false, description = "Up to 10 exact file/directory paths. Omitted means the whole repository.") List<String> filePaths,
            @ToolParam(required = false, description = "Inclusive ISO-8601 timestamp with timezone.") String since,
            @ToolParam(required = false, description = "Inclusive ISO-8601 timestamp with timezone.") String until,
            @ToolParam(required = false, description = "Continuation token from this operation; keep the same filters.") String cursor,
            @ToolParam(description = "One short Polish sentence explaining the purpose.") String reason,
            ToolContext toolContext) {
        requireReason(reason);
        var scope = target(projectName, ref, toolContext);
        var query = new HistoryQuery(scope.group(), scope.projectName(), scope.branch(), paths(filePaths), instant(since), instant(until));
        if (query.since() != null && query.until() != null && query.since().isAfter(query.until())) {
            throw new IllegalArgumentException("since must not exceed until.");
        }
        Map<String, Integer> pages = new LinkedHashMap<>();
        String head;
        if (cursor != null) {
            var state = continuation(cursor, HistoryCursor.class);
            if (!query.equals(state.query())) throw new IllegalArgumentException("History cursor filters changed.");
            GitLabHistoryCommit.requireSha(state.head());
            head = state.head();
            pages.putAll(state.pages());
            if (pages.isEmpty() || pages.size() > MAX_PATHS || pages.entrySet().stream().anyMatch(e ->
                    (!e.getKey().isEmpty() && !query.paths().contains(e.getKey())) || e.getValue() < 1)) {
                throw new IllegalArgumentException("Invalid history cursor pages.");
            }
        } else {
            if (scope.pinned()) {
                head = scope.readRef();
            } else {
                var revision = revisions.resolveRevision(scope.group(), scope.projectName(), scope.readRef());
                if (revision == null || !scope.group().equals(revision.group())
                        || !scope.projectName().equals(revision.projectName()) || !scope.readRef().equals(revision.ref())) {
                    throw new IllegalStateException("History revision could not be resolved.");
                }
                head = revision.commitId();
            }
            GitLabHistoryCommit.requireSha(head);
            (query.paths().isEmpty() ? List.of("") : query.paths()).forEach(path -> pages.put(path, 1));
        }
        var commits = new LinkedHashMap<String, GitLabHistoryCommit>();
        var matches = new LinkedHashMap<String, Set<String>>();
        var next = new LinkedHashMap<String, Integer>();
        var limits = new ArrayList<String>();
        for (var entry : pages.entrySet()) {
            try {
                var page = history.listCommits(scope.group(), query.project(), head, entry.getKey(),
                        query.since(), query.until(), entry.getValue());
                for (var commit : page.commits()) {
                    commits.putIfAbsent(commit.id(), commit);
                    var matched = matches.computeIfAbsent(commit.id(), ignored -> new LinkedHashSet<>());
                    if (!entry.getKey().isEmpty()) matched.add(entry.getKey());
                }
                if (page.nextPage() != null) next.put(entry.getKey(), page.nextPage());
            } catch (RuntimeException failure) {
                // Do not expose credentials or raw remote response bodies in tool evidence.
                limits.add("History page unavailable for path '" + entry.getKey() + "', page " + entry.getValue() + ". Retry with nextCursor.");
                next.put(entry.getKey(), entry.getValue());
            }
        }
        var rows = commits.values().stream().sorted(Comparator.comparing(GitLabHistoryCommit::committedAt).reversed()
                        .thenComparing(GitLabHistoryCommit::id))
                .map(c -> new CommitMatch(c, List.copyOf(matches.get(c.id())), source(scope.group(), query.project(), c.id()))).toList();
        var token = next.isEmpty() ? null : cursor(new HistoryCursor(query, head, Map.copyOf(next)));
        limits.add("matchedPaths covers this batch only; commits may recur in later batches for other paths. Rename history depends on the server.");
        return new CommitResult(scope.group() + "/" + query.project(), head, rows, token, limits);
    }

    @Tool(name = GitLabToolNames.READ_COMMIT_DIFF, description = "Read a bounded diff for any full commit SHA. No previous history lookup is required. "
            + "Commit IDs from the user, earlier conversations or other tools are accepted. Continue with nextCursor and identical filters. "
            + "Server diff limits may omit files or patches; the end of pagination does not prove a complete diff. "
            + "Read before/after files using the SHA or parent SHA as branchRef in gitlab_read_repository_file or gitlab_read_repository_file_chunk for the same project when needed.")
    public DiffResult readCommitDiff(
            @ToolParam(description = "Exact project path relative to the configured group.") String projectName,
            @ToolParam(description = "Full commit SHA.") String commitId,
            @ToolParam(required = false, description = "Up to 10 exact old or new paths; omitted means all changed paths.") List<String> filePaths,
            @ToolParam(required = false, description = "Continuation token from this operation; keep the same filters.") String cursor,
            @ToolParam(description = "One short Polish sentence explaining the purpose.") String reason,
            ToolContext toolContext) {
        requireReason(reason);
        GitLabHistoryCommit.requireSha(commitId);
        var scope = target(projectName, commitId, toolContext);
        var query = new DiffQuery(scope.group(), scope.projectName(), commitId, paths(filePaths));
        var state = cursor == null ? new DiffCursor(query, 1, 0, 0) : continuation(cursor, DiffCursor.class);
        if (!query.equals(state.query())) throw new IllegalArgumentException("Diff cursor filters changed.");
        if (state.page() < 1 || state.index() < 0 || state.offset() < 0) throw new IllegalArgumentException("Invalid diff cursor.");
        var page = history.readDiff(scope.group(), query.project(), query.sha(), state.page());
        var files = page.files().stream()
                .filter(f -> query.paths().isEmpty() || query.paths().contains(f.oldPath()) || query.paths().contains(f.newPath())).toList();
        if (state.index() > files.size()) throw new IllegalArgumentException("Diff cursor file is unavailable.");
        var limits = new ArrayList<String>();
        limits.add("GitLab server limits can omit files/patches. Pagination end is not proof of completeness; merge diffs do not describe every parent.");
        int index = state.index();
        int offset = state.offset();
        int remaining = PATCH_CHARACTERS;
        var slices = new ArrayList<GitLabDiffMcpTools.PatchSlice>();
        while (index < files.size() && remaining > 0) {
            var file = files.get(index);
            var patch = file.patch() == null ? "" : file.patch();
            if (offset > patch.length()) throw new IllegalArgumentException("Diff cursor offset is unavailable.");
            int end = Math.min(patch.length(), offset + remaining);
            boolean unavailable = patch.isEmpty() || file.collapsed() || file.tooLarge();
            slices.add(new GitLabDiffMcpTools.PatchSlice(file.oldPath(), file.newPath(), file.newFile(), file.renamedFile(),
                    file.deletedFile(), patch.substring(offset, end), offset, patch.length(), end < patch.length(),
                    unavailable, file.collapsed(), file.tooLarge()));
            if (unavailable) limits.add("Patch unavailable or empty: " + file.newPath());
            remaining -= end - offset;
            if (end < patch.length()) { offset = end; break; }
            index++;
            offset = 0;
        }
        String token = null;
        if (index < files.size()) {
            token = cursor(new DiffCursor(query, state.page(), index, offset));
        } else if (page.nextPage() != null) {
            token = cursor(new DiffCursor(query, page.nextPage(), 0, 0));
        }
        return new DiffResult(scope.group() + "/" + query.project(), commitId, slices, token,
                source(scope.group(), query.project(), commitId), limits);
    }

    private GitLabRepositoryToolScope.Target target(String project, String ref, ToolContext context) {
        var hidden = context == null ? Map.<String, Object>of() : context.getContext();
        var requestedRef = ref == null || ref.isBlank() ? (String) hidden.get(AgentToolContextKeys.GITLAB_BRANCH) : ref;
        var bound = hidden.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        if (bound instanceof GitLabRepositoryToolScope scope) {
            return scope.resolve(project, requestedRef == null ? scope.selectedBranch() : requestedRef, revisions);
        }
        var scope = new GitLabToolScopeResolver(settings, operationalContext, navigation)
                .resolve(requestedRef, project, null, context);
        var relative = GitLabRepositoryToolScope.projectPathInGroup(scope.group(), project).substring(scope.group().length() + 1);
        return new GitLabRepositoryToolScope.Target(scope.group(), relative, scope.branch(), scope.branch(), false);
    }

    private static String cursor(Object state) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(CURSORS.writeValueAsBytes(state));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not encode history continuation.", e);
        }
    }

    private static <T> T continuation(String cursor, Class<T> type) {
        try {
            if (cursor.length() > 32_000) throw new IllegalArgumentException("Invalid history cursor.");
            return CURSORS.readValue(Base64.getUrlDecoder().decode(cursor), type);
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid history continuation.", e);
        }
    }
    private static List<String> paths(List<String> paths) {
        if (paths == null) return List.of();
        if (paths.size() > MAX_PATHS || paths.stream().anyMatch(p -> !GitLabRepositoryPath.isSafePath(p, false))) {
            throw new IllegalArgumentException("filePaths must contain at most 10 safe paths.");
        }
        return paths.stream().distinct().sorted().toList();
    }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A short reason is required.");
    }
    private static String source(String group, String project, String sha) {
        return "gitlab:" + group + "/" + project + "@" + sha;
    }
    private record HistoryQuery(String group, String project, String ref, List<String> paths, Instant since, Instant until) { }
    private record HistoryCursor(HistoryQuery query, String head, Map<String, Integer> pages) { }
    private record DiffQuery(String group, String project, String sha, List<String> paths) { }
    private record DiffCursor(DiffQuery query, int page, int index, int offset) { }
    public record CommitMatch(GitLabHistoryCommit commit, List<String> matchedPaths, String sourceRef) { }
    public record CommitResult(String projectPath, String historyHeadSha, List<CommitMatch> commits,
                               String nextCursor, List<String> limitations) { }
    public record DiffResult(String projectPath, String commitId, List<GitLabDiffMcpTools.PatchSlice> files,
                             String nextCursor, String sourceRef, List<String> limitations) { }
}
