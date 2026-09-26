package pl.mkn.tdw.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Renders a path-only repository snapshot without fetching or inferring file contents. */
public final class RepositoryPathTreeRenderer {

    private static final Comparator<Node> NODE_ORDER = Comparator
            .comparing((Node node) -> !node.directory)
            .thenComparing(node -> node.name);

    private RepositoryPathTreeRenderer() {
    }

    public static String render(String projectPath, List<Entry> entries) {
        var rootName = rootName(projectPath);
        var snapshot = Objects.requireNonNull(entries, "Repository tree entries are required.");
        var directories = new HashSet<String>();
        for (var entry : snapshot) {
            var checked = Objects.requireNonNull(entry, "Repository tree entry is required.");
            validatePath(checked.path());
            if (checked.directory()) {
                directories.add(checked.path());
            }
        }
        var root = new Node(rootName, true);
        for (var entry : snapshot) {
            requireObservedParents(entry.path(), directories);
            add(root, entry);
        }

        var result = new StringBuilder(rootName).append('/');
        appendChildren(result, root, "");
        return result.toString();
    }

    private static String rootName(String projectPath) {
        if (projectPath == null || projectPath.isBlank() || projectPath.endsWith("/")
                || projectPath.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Repository project path is required.");
        }
        var name = projectPath.substring(projectPath.lastIndexOf('/') + 1);
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("Repository project name is invalid.");
        }
        return name;
    }

    private static void add(Node root, Entry entry) {
        var path = entry.path();
        var segments = path.split("/", -1);
        var parent = root;
        for (int index = 0; index < segments.length; index++) {
            var name = segments[index];
            var directory = index < segments.length - 1 || entry.directory();
            var child = parent.children.get(name);
            if (child == null) {
                child = new Node(name, directory);
                parent.children.put(name, child);
            } else if (child.directory != directory) {
                throw new IllegalArgumentException("Repository tree path has conflicting types: " + path);
            }
            parent = child;
        }
    }

    private static void validatePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.endsWith("/")
                || path.contains("//") || path.contains("\\")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Repository tree path is invalid.");
        }
        var segments = path.split("/", -1);
        for (var name : segments) {
            if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
                throw new IllegalArgumentException("Repository tree segment is invalid.");
            }
        }
    }

    private static void requireObservedParents(String path, Set<String> directories) {
        var slash = path.lastIndexOf('/');
        while (slash > 0) {
            var parent = path.substring(0, slash);
            if (!directories.contains(parent)) {
                throw new IllegalArgumentException("Repository tree parent was not observed: " + parent);
            }
            slash = parent.lastIndexOf('/');
        }
    }

    private static void appendChildren(StringBuilder result, Node parent, String prefix) {
        var children = new ArrayList<>(parent.children.values());
        children.sort(NODE_ORDER);
        for (int index = 0; index < children.size(); index++) {
            var child = children.get(index);
            var last = index == children.size() - 1;
            result.append('\n').append(prefix).append(last ? "└── " : "├── ")
                    .append(child.name).append(child.directory ? "/" : "");
            if (child.directory) {
                appendChildren(result, child, prefix + (last ? "    " : "│   "));
            }
        }
    }

    public record Entry(String path, boolean directory) {
    }

    private static final class Node {
        private final String name;
        private final boolean directory;
        private final Map<String, Node> children = new HashMap<>();

        private Node(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
        }
    }
}
