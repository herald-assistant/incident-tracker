package pl.mkn.tdw.features.uxinspector.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds a best-effort, pinned source pack for every component already found by
 * the selected screen reachability graph. A missing file is evidence for the
 * model, never a reason to stop UX Inspector preparation.
 */
@Service
@RequiredArgsConstructor
public class UxInspectorComponentSourcePackArtifactService {
    static final String SCHEMA = "tdw.ux-inspector-component-source-pack";
    static final int VERSION = 1;

    private final GitLabRepositoryPort repositoryPort;

    public UxInspectorComponentSourcePackArtifact prepare(
            UxInspectorTargetContext context,
            UxInspectorCapture capture
    ) {
        var components = orderedComponents(context);
        var files = readFiles(context, components);
        var availablePaths = new LinkedHashSet<String>();
        files.values().stream().filter(PackFile::available).map(PackFile::path).forEach(availablePaths::add);
        var availableCount = (int) files.values().stream().filter(PackFile::available).count();
        return new UxInspectorComponentSourcePackArtifact(
                render(context, capture, components, files),
                components.size(), files.size(), availableCount, files.size() - availableCount,
                availablePaths
        );
    }

    private List<GitLabFrontendReachabilityComponent> orderedComponents(UxInspectorTargetContext context) {
        if (context == null || context.graph() == null) return List.of();
        var byId = new LinkedHashMap<String, GitLabFrontendReachabilityComponent>();
        context.graph().componentLevels().stream()
                .sorted(Comparator.comparingInt(level -> level.depth()))
                .flatMap(level -> level.components().stream()
                        .sorted(Comparator.comparingInt(GitLabFrontendReachabilityComponent::breadthFirstOrder)
                                .thenComparing(GitLabFrontendReachabilityComponent::componentId)))
                .forEach(component -> byId.putIfAbsent(component.componentId(), component));
        return List.copyOf(byId.values());
    }

    private Map<String, PackFile> readFiles(
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        var paths = new LinkedHashSet<String>();
        components.forEach(component -> {
            addPath(paths, component.sourcePath());
            addPath(paths, component.templatePath());
        });
        var files = new LinkedHashMap<String, PackFile>();
        for (var path : paths) files.put(path, readFile(context, path));
        return files;
    }

    private PackFile readFile(UxInspectorTargetContext context, String path) {
        if (!GitLabVerifiedRepositoryFileReader.isSafePath(path, false)) {
            return PackFile.unavailable(path, "UNSAFE_OR_INVALID_PATH");
        }
        if (context == null || context.sourceScope() == null || context.sourceRevision() == null
                || !StringUtils.hasText(context.sourceScope().group())
                || !StringUtils.hasText(context.sourceScope().projectName())
                || !StringUtils.hasText(context.sourceRevision().revision())) {
            return PackFile.unavailable(path, "PINNED_REPOSITORY_SCOPE_UNAVAILABLE");
        }
        try {
            var file = GitLabVerifiedRepositoryFileReader.read(
                    repositoryPort,
                    context.sourceScope().group(),
                    context.sourceScope().projectName(),
                    context.sourceRevision().revision(),
                    path,
                    GitLabVerifiedRepositoryFileReader.MAX_FILE_BYTES
            );
            return PackFile.available(file.path(), file.content(), file.sizeBytes());
        } catch (RuntimeException exception) {
            return PackFile.unavailable(path, "VERIFIED_PINNED_READ_FAILED");
        }
    }

    private String render(
            UxInspectorTargetContext context,
            UxInspectorCapture capture,
            List<GitLabFrontendReachabilityComponent> components,
            Map<String, PackFile> files
    ) {
        var available = files.values().stream().filter(PackFile::available).count();
        var builder = new StringBuilder();
        builder.append("schema: ").append(SCHEMA).append('\n');
        builder.append("version: ").append(VERSION).append('\n');
        builder.append("semantics: STATIC_SCREEN_REACHABILITY_NOT_RUNTIME_ANCESTRY\n");
        builder.append("ordering: graph depth ascending, then breadth-first discovery order\n");
        builder.append("componentCount: ").append(components.size()).append('\n');
        builder.append("uniqueFileCount: ").append(files.size()).append('\n');
        builder.append("availableFileCount: ").append(available).append('\n');
        builder.append("unavailableFileCount: ").append(files.size() - available).append('\n');
        if (context != null && context.sourceRevision() != null) {
            builder.append("pinnedCommit: ").append(context.sourceRevision().revision()).append('\n');
        }
        var complete = !components.isEmpty() && components.stream().allMatch(component ->
                StringUtils.hasText(component.sourcePath())
                        && available(files, component.sourcePath())
                        && (!StringUtils.hasText(component.templatePath())
                        || available(files, component.templatePath())));
        builder.append("complete: ").append(complete).append('\n');

        builder.append("\n## Runtime component boundaries\n");
        var boundaries = capture != null && capture.target() != null && capture.target().domFingerprint() != null
                ? capture.target().domFingerprint().componentBoundaryTags() : List.<String>of();
        if (boundaries == null || boundaries.isEmpty()) {
            builder.append("- none captured\n");
        } else {
            for (var index = 0; index < boundaries.size(); index++) {
                var boundary = boundaries.get(index);
                var matches = components.stream()
                        .filter(component -> StringUtils.hasText(component.selector())
                                && component.selector().equalsIgnoreCase(boundary))
                        .map(GitLabFrontendReachabilityComponent::componentId).toList();
                builder.append("- distance=").append(index + 1).append(" tag=").append(boundary)
                        .append(" status=").append(matches.isEmpty() ? "NOT_FOUND_IN_STATIC_GRAPH" : "MATCHED")
                        .append(matches.isEmpty() ? "" : " components=" + String.join(",", matches)).append('\n');
            }
        }

        builder.append("\n## Discovered components\n");
        if (components.isEmpty()) builder.append("- none; the static graph did not expose a component\n");
        for (var index = 0; index < components.size(); index++) {
            renderComponent(builder, index + 1, components.get(index), context, files);
        }

        builder.append("\n## Unresolved discovery information\n");
        var unresolved = unresolved(context, components);
        if (unresolved.isEmpty()) builder.append("- none reported by static discovery\n");
        else unresolved.forEach(value -> builder.append("- ").append(value).append('\n'));

        builder.append("\n## Full verified component files\n");
        if (files.isEmpty()) builder.append("- none discovered\n");
        var fileIndex = 0;
        for (var file : files.values()) {
            builder.append("\n### File ").append(++fileIndex).append('\n');
            builder.append("path: ").append(file.path()).append('\n');
            builder.append("status: ").append(file.available() ? "AVAILABLE" : "UNAVAILABLE").append('\n');
            if (file.available()) {
                builder.append("sizeBytes: ").append(file.sizeBytes()).append('\n');
                builder.append("content:\nBEGIN_UNTRUSTED_COMPONENT_FILE ").append(file.path()).append('\n');
                builder.append(file.content());
                if (!file.content().endsWith("\n")) builder.append('\n');
                builder.append("END_UNTRUSTED_COMPONENT_FILE ").append(file.path()).append('\n');
            } else {
                builder.append("reason: ").append(file.reason()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private void renderComponent(
            StringBuilder builder,
            int ordinal,
            GitLabFrontendReachabilityComponent component,
            UxInspectorTargetContext context,
            Map<String, PackFile> files
    ) {
        builder.append("\n### Component ").append(ordinal).append('\n');
        builder.append("componentId: ").append(component.componentId()).append('\n');
        builder.append("depth: ").append(component.depth()).append('\n');
        builder.append("breadthFirstOrder: ").append(component.breadthFirstOrder()).append('\n');
        builder.append("symbol: ").append(text(component.symbol())).append('\n');
        builder.append("selector: ").append(text(component.selector())).append('\n');
        builder.append("discoveryKind: ").append(text(component.discoveryKind())).append('\n');
        builder.append("discoveryStatus: ").append(text(component.status())).append('\n');
        builder.append("sourceFile: ").append(fileStatus(component.sourcePath(), files)).append('\n');
        builder.append("templateFile: ").append(StringUtils.hasText(component.templatePath())
                ? fileStatus(component.templatePath(), files) : "INLINE_OR_NOT_DISCOVERED").append('\n');
        builder.append("sourceSliceTruncatedByGraph: ").append(component.truncated()).append('\n');
        builder.append("dependencyIds: ").append(component.dependencyIds().isEmpty()
                ? "[]" : String.join(", ", component.dependencyIds())).append('\n');
        builder.append("childComponentIds: ").append(component.childComponentIds().isEmpty()
                ? "[]" : String.join(", ", component.childComponentIds())).append('\n');
        var edges = context != null && context.graph() != null ? context.graph().edges() : List.<GitLabFrontendReachabilityEdge>of();
        var incoming = edges.stream().filter(edge -> component.componentId().equals(edge.toId()))
                .map(edge -> edge.fromId() + " --" + edge.kind() + "--> " + edge.toId()).toList();
        var outgoing = edges.stream().filter(edge -> component.componentId().equals(edge.fromId()))
                .map(edge -> edge.fromId() + " --" + edge.kind() + "--> " + edge.toId()).toList();
        builder.append("incomingEdges: ").append(incoming.isEmpty() ? "[]" : String.join(" | ", incoming)).append('\n');
        builder.append("outgoingEdges: ").append(outgoing.isEmpty() ? "[]" : String.join(" | ", outgoing)).append('\n');
        if (!component.limitations().isEmpty()) {
            builder.append("limitations: ").append(String.join(" | ", component.limitations())).append('\n');
        }
    }

    private List<String> unresolved(
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        var values = new ArrayList<String>();
        if (context == null || context.graph() == null) {
            values.add("COMPONENT_GRAPH_UNAVAILABLE");
            return values;
        }
        context.graph().limitations().forEach(value -> values.add("GRAPH_LIMITATION: " + value));
        context.graph().diagnostics().forEach(value -> values.add("GRAPH_DIAGNOSTIC: " + value.severity()
                + " " + value.code() + " - " + value.message()));
        components.forEach(component -> component.limitations().forEach(value ->
                values.add("COMPONENT_LIMITATION " + component.componentId() + ": " + value)));
        return values.stream().distinct().toList();
    }

    private String fileStatus(String path, Map<String, PackFile> files) {
        if (!StringUtils.hasText(path)) return "NOT_DISCOVERED";
        var file = files.get(path.trim().replace('\\', '/'));
        if (file == null) return path + " [NOT_DISCOVERED]";
        return file.path() + " [" + (file.available() ? "AVAILABLE_FULL" : "UNAVAILABLE:" + file.reason()) + "]";
    }

    private boolean available(Map<String, PackFile> files, String path) {
        if (!StringUtils.hasText(path)) return false;
        var file = files.get(path.trim().replace('\\', '/'));
        return file != null && file.available();
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim().replace('\n', ' ').replace('\r', ' ') : "null";
    }

    private void addPath(LinkedHashSet<String> paths, String path) {
        if (StringUtils.hasText(path)) paths.add(path.trim().replace('\\', '/'));
    }

    private record PackFile(String path, String content, int sizeBytes, String reason) {
        static PackFile available(String path, String content, int sizeBytes) {
            return new PackFile(path, content, sizeBytes, null);
        }

        static PackFile unavailable(String path, String reason) {
            return new PackFile(path, "", 0, reason);
        }

        boolean available() {
            return reason == null;
        }
    }
}
