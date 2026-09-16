package pl.mkn.tdw.features.uxinspector.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/** Renders a complete path-only view of the first four repository levels for the pinned revision. */
@Service
@RequiredArgsConstructor
public class UxInspectorRepositoryTreeArtifactService {

    static final int DEPTH = 4;
    private static final int PAGE_SIZE = 100;

    private final GitLabRepositoryPort repositoryPort;

    public UxInspectorRepositoryTreeArtifact prepare(UxInspectorTargetContext context) {
        if (context == null || context.sourceScope() == null || context.sourceRevision() == null) {
            throw unavailable();
        }
        var group = context.sourceScope().group();
        var project = context.sourceScope().projectName();
        var branch = context.sourceRevision().branch();
        var commit = context.sourceRevision().revision();
        if (!StringUtils.hasText(group) || !StringUtils.hasText(project)
                || !StringUtils.hasText(branch) || !StringUtils.hasText(commit)) {
            throw unavailable();
        }

        final List<TreeEntry> entries;
        try {
            entries = load(group.trim(), project.trim(), commit.trim());
        } catch (UxInspectorContextException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }

        var builder = new StringBuilder();
        builder.append("repository: ").append(group.trim()).append('/').append(project.trim()).append('\n');
        builder.append("branch: ").append(branch.trim()).append('\n');
        builder.append("commit: ").append(commit.trim()).append('\n');
        builder.append("depth: ").append(DEPTH).append('\n');
        builder.append("content: PATH_NAMES_ONLY\n");
        builder.append("complete: true\n");
        builder.append("paths:");
        if (entries.isEmpty()) {
            builder.append(" []");
        } else {
            for (var entry : entries) {
                builder.append("\n- [").append("tree".equals(entry.type()) ? "dir" : "file")
                        .append("] ").append(entry.path());
            }
        }
        return new UxInspectorRepositoryTreeArtifact(
                builder.toString(),
                entries.stream().filter(entry -> "blob".equals(entry.type())).map(TreeEntry::path).toList()
        );
    }

    private List<TreeEntry> load(String group, String project, String commit) {
        var pending = new ArrayDeque<Directory>();
        pending.add(new Directory("", 0));
        var scheduledDirectories = new HashSet<String>();
        scheduledDirectories.add("");
        var entries = new LinkedHashMap<String, TreeEntry>();

        while (!pending.isEmpty()) {
            var directory = pending.removeFirst();
            var seenCursors = new HashSet<String>();
            var cursor = "";
            do {
                if (!seenCursors.add(cursor)) {
                    throw unavailable();
                }
                var page = repositoryPort.listRepositoryTreeChildrenPage(
                        group, project, commit, directory.path(), cursor, PAGE_SIZE);
                if (page == null || page.nodes() == null || page.nodes().size() > PAGE_SIZE) {
                    throw unavailable();
                }
                for (var node : page.nodes()) {
                    if (!isDirectChild(directory.path(), node)) {
                        throw unavailable();
                    }
                    var entry = new TreeEntry(node.path(), node.type());
                    var previous = entries.putIfAbsent(entry.path(), entry);
                    if (previous != null && !previous.type().equals(entry.type())) {
                        throw unavailable();
                    }
                    var childLevel = directory.level() + 1;
                    if ("tree".equals(entry.type()) && childLevel < DEPTH
                            && scheduledDirectories.add(entry.path())) {
                        pending.addLast(new Directory(entry.path(), childLevel));
                    }
                }
                cursor = StringUtils.hasText(page.nextCursor()) ? page.nextCursor().trim() : null;
            } while (cursor != null);
        }

        return entries.values().stream()
                .sorted(java.util.Comparator.comparing(TreeEntry::path))
                .toList();
    }

    private boolean isDirectChild(String directory, GitLabRepositoryTreeNode node) {
        if (node == null || !StringUtils.hasText(node.path())
                || !("tree".equals(node.type()) || "blob".equals(node.type()))
                || !GitLabVerifiedRepositoryFileReader.isSafePath(node.path(), false)) {
            return false;
        }
        var prefix = directory.isEmpty() ? "" : directory + "/";
        if (!node.path().startsWith(prefix)) {
            return false;
        }
        var name = node.path().substring(prefix.length());
        return StringUtils.hasText(name) && !name.contains("/");
    }

    private UxInspectorContextException unavailable() {
        return new UxInspectorContextException(
                "UX_INSPECTOR_REPOSITORY_TREE_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "The complete four-level repository tree could not be loaded for the pinned source revision."
        );
    }

    private record Directory(String path, int level) {
    }

    private record TreeEntry(String path, String type) {
    }
}
