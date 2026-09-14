package pl.mkn.tdw.integrations.gitlab;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded breadth-first navigation over one immutable GitLab revision. */
@Service
@RequiredArgsConstructor
public class GitLabRepositoryTreeExplorer {

    public static final int DEPTH = 4;
    private static final int MAX_REQUESTS = 12;
    private static final int MAX_ENTRIES = 120;
    private static final int PAGE_ENTRIES = 40;
    private static final int MAX_CONTINUATIONS = 48;
    private static final Pattern CURSOR = Pattern.compile("[A-Za-z0-9_-]{1,2048}");

    private final GitLabRepositoryPort repositoryPort;

    public GitLabRepositoryTreeSlice explore(
            String group, String project, String commit, String path, String cursor
    ) {
        var root = safePath(path);
        if (cursor != null && !cursor.isBlank() && !CURSOR.matcher(cursor).matches()) {
            throw new IllegalArgumentException("Invalid GitLab tree cursor.");
        }
        var pending = new ArrayDeque<Directory>();
        pending.add(new Directory(root, cursor == null ? "" : cursor, 0));
        var entries = new ArrayList<GitLabRepositoryTreeSlice.Entry>();
        var continuations = new ArrayList<GitLabRepositoryTreeSlice.Continuation>();
        var requests = 0;

        while (!pending.isEmpty() && requests < MAX_REQUESTS && entries.size() < MAX_ENTRIES) {
            var directory = pending.removeFirst();
            var requestedEntries = Math.min(PAGE_ENTRIES, MAX_ENTRIES - entries.size());
            var page = repositoryPort.listRepositoryTreeChildrenPage(
                    group, project, commit, directory.path(), directory.cursor(),
                    requestedEntries
            );
            requests++;
            if (page == null || page.nodes().size() > requestedEntries
                    || page.nextCursor() != null && !CURSOR.matcher(page.nextCursor()).matches()) {
                throw new IllegalStateException("GitLab directory page exceeds the bounded contract.");
            }
            for (var node : page.nodes()) {
                if (!isSafeDirectChild(directory.path(), node)) {
                    continue;
                }
                entries.add(new GitLabRepositoryTreeSlice.Entry(node.path(), node.type()));
                if ("tree".equals(node.type()) && directory.level() + 1 < DEPTH) {
                    pending.addLast(new Directory(node.path(), "", directory.level() + 1));
                }
            }
            if (page.nextCursor() != null) {
                continuations.add(new GitLabRepositoryTreeSlice.Continuation(
                        directory.path(), page.nextCursor()));
            }
        }
        var truncated = !continuations.isEmpty() || !pending.isEmpty();
        while (!pending.isEmpty() && continuations.size() < MAX_CONTINUATIONS) {
            var directory = pending.removeFirst();
            continuations.add(new GitLabRepositoryTreeSlice.Continuation(
                    directory.path(), directory.cursor().isBlank() ? null : directory.cursor()));
        }
        return new GitLabRepositoryTreeSlice(root, DEPTH, entries, continuations, truncated);
    }

    private String safePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.length() > 1_024 || !path.equals(path.trim()) || path.startsWith("/")
                || path.endsWith("/") || path.contains("//") || path.contains("\\")
                || path.contains("?") || path.contains("#") || path.contains("%")
                || path.contains(":") || path.contains("@{")) {
            throw new IllegalArgumentException("A safe relative GitLab directory is required.");
        }
        for (var segment : path.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("A safe relative GitLab directory is required.");
            }
        }
        if (path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("A safe relative GitLab directory is required.");
        }
        return path;
    }

    private boolean isSafeDirectChild(String directory, GitLabRepositoryTreeNode node) {
        if (node == null || node.path() == null || node.path().length() > 512
                || !("tree".equals(node.type()) || "blob".equals(node.type()))) {
            return false;
        }
        var path = node.path();
        if (!directory.isEmpty() && !path.startsWith(directory + "/")) {
            return false;
        }
        var childName = directory.isEmpty() ? path : path.substring(directory.length() + 1);
        if (childName.isBlank() || childName.contains("/")) {
            return false;
        }
        try {
            safePath(path);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record Directory(String path, String cursor, int level) {
    }
}
