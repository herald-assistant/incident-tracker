package pl.mkn.tdw.features.uxinspector.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetCandidate;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdgeKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds a best-effort, pinned component index and a deliberately small source
 * pack. Full files are limited to the best target-to-view path; the remaining
 * graph stays navigable without paying the initial-context cost of every file.
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
        var selection = selectFullSourceComponents(context, components);
        var fullSourceComponents = components.stream()
                .filter(component -> selection.componentIds().contains(component.componentId()))
                .toList();
        var files = readFiles(context, fullSourceComponents);
        var availablePaths = new LinkedHashSet<String>();
        files.values().stream().filter(PackFile::available).map(PackFile::path).forEach(availablePaths::add);
        var availableCount = (int) files.values().stream().filter(PackFile::available).count();
        return new UxInspectorComponentSourcePackArtifact(
                render(context, capture, components, selection, files),
                components.size(), fullSourceComponents.size(), components.size() - fullSourceComponents.size(),
                files.size(), availableCount, files.size() - availableCount,
                availablePaths
        );
    }

    private SourceSelection selectFullSourceComponents(
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        if (components.isEmpty()) {
            return new SourceSelection("NO_COMPONENTS", Set.of(), List.of(),
                    List.of("No component was available for full-source selection."));
        }
        var byId = new LinkedHashMap<String, GitLabFrontendReachabilityComponent>();
        components.forEach(component -> byId.put(component.componentId(), component));
        var limitations = new ArrayList<String>();
        var viewComponent = findViewComponent(context, components);
        if (viewComponent == null) {
            limitations.add("Selected view component was not identified from the route target; no arbitrary graph component was promoted to full source.");
            return new SourceSelection("VIEW_COMPONENT_NOT_FOUND", Set.of(), List.of(), List.copyOf(limitations));
        }

        if (context == null || context.status() == UxInspectorTargetResolutionStatus.NOT_FOUND) {
            return new SourceSelection("NOT_FOUND_VIEW_COMPONENT_ONLY", Set.of(viewComponent.componentId()),
                    List.of(new SelectedPath("unresolved-target", List.of(viewComponent.componentId()), 0, false)), limitations);
        }

        var candidates = context.status() == UxInspectorTargetResolutionStatus.AMBIGUOUS
                ? context.candidates().stream().limit(3).toList()
                : context.candidates().stream().limit(1).toList();
        var selectedIds = new LinkedHashSet<String>();
        var selectedPaths = new ArrayList<SelectedPath>();
        for (var candidate : candidates) {
            var target = findCandidateComponent(candidate, components);
            if (target == null) {
                limitations.add("Candidate " + candidate.candidateId() + " was not found in the static component graph.");
                continue;
            }
            var path = shortestRenderingPath(target.componentId(), viewComponent.componentId(), context, byId);
            if (path == null) {
                selectedIds.add(target.componentId());
                selectedPaths.add(new SelectedPath(candidate.candidateId(), List.of(target.componentId()), 0, false));
                limitations.add("No rendering path from candidate " + candidate.candidateId()
                        + " to selected view component " + viewComponent.componentId() + " was found; only the candidate source was included.");
            } else {
                selectedIds.addAll(path.componentIds());
                selectedPaths.add(new SelectedPath(candidate.candidateId(), path.componentIds(), path.cost(), true));
            }
        }
        if (selectedIds.isEmpty()) {
            selectedIds.add(viewComponent.componentId());
            selectedPaths.add(new SelectedPath("unresolved-candidate", List.of(viewComponent.componentId()), 0, false));
            limitations.add("No resolved candidate could seed a component path; only the selected view component source was included.");
        }
        var strategy = context.status() == UxInspectorTargetResolutionStatus.AMBIGUOUS
                ? "AMBIGUOUS_UNION_OF_UP_TO_THREE_TARGET_TO_VIEW_PATHS"
                : "RESOLVED_SHORTEST_TARGET_TO_VIEW_PATH";
        return new SourceSelection(strategy, Set.copyOf(selectedIds), List.copyOf(selectedPaths), List.copyOf(limitations));
    }

    private GitLabFrontendReachabilityComponent findViewComponent(
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        if (context == null || context.graph() == null || context.graph().screenNode() == null) return null;
        var routeTarget = context.graph().screenNode().viewTarget();
        if (routeTarget == null && context.graph().screenNode().screen() != null) {
            routeTarget = context.graph().screenNode().screen().viewTarget();
        }
        if (routeTarget == null) return null;
        var sourcePath = normalizedPath(routeTarget.sourcePath());
        var symbol = routeTarget.symbol();
        return components.stream().filter(component ->
                        (sourcePath != null && sourcePath.equals(normalizedPath(component.sourcePath())))
                                || (StringUtils.hasText(symbol) && symbol.equals(component.symbol())))
                .findFirst().orElse(null);
    }

    private GitLabFrontendReachabilityComponent findCandidateComponent(
            UxInspectorTargetCandidate candidate,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        if (candidate == null) return null;
        var candidatePath = normalizedPath(candidate.sourcePath());
        return components.stream().filter(component ->
                        component.componentId().equals(candidate.componentId())
                                || (candidatePath != null && candidatePath.equals(normalizedPath(component.sourcePath())))
                                || (StringUtils.hasText(candidate.symbol()) && candidate.symbol().equals(component.symbol())))
                .findFirst().orElse(null);
    }

    private PathResult shortestRenderingPath(
            String targetId,
            String viewId,
            UxInspectorTargetContext context,
            Map<String, GitLabFrontendReachabilityComponent> components
    ) {
        if (targetId.equals(viewId)) return new PathResult(List.of(targetId), 0);
        if (context == null || context.graph() == null) return null;
        var incoming = new LinkedHashMap<String, List<GitLabFrontendReachabilityEdge>>();
        context.graph().edges().stream()
                .filter(edge -> renderingWeight(edge.kind()) != null)
                .filter(edge -> components.containsKey(edge.fromId()) && components.containsKey(edge.toId()))
                .forEach(edge -> incoming.computeIfAbsent(edge.toId(), ignored -> new ArrayList<>()).add(edge));
        incoming.values().forEach(edges -> edges.sort(Comparator
                .comparingInt((GitLabFrontendReachabilityEdge edge) -> renderingWeight(edge.kind()))
                .thenComparing(GitLabFrontendReachabilityEdge::fromId)
                .thenComparing(edge -> edge.kind().name())));

        var queue = new PriorityQueue<PathResult>(Comparator.comparingInt(PathResult::cost)
                .thenComparingInt(path -> path.componentIds().size())
                .thenComparing(path -> String.join("/", path.componentIds())));
        queue.add(new PathResult(List.of(targetId), 0));
        var bestCost = new LinkedHashMap<String, Integer>();
        while (!queue.isEmpty()) {
            var current = queue.remove();
            var currentId = current.componentIds().get(current.componentIds().size() - 1);
            if (current.cost() > bestCost.getOrDefault(currentId, Integer.MAX_VALUE)) continue;
            if (currentId.equals(viewId)) return current;
            for (var edge : incoming.getOrDefault(currentId, List.of())) {
                if (current.componentIds().contains(edge.fromId())) continue;
                var nextCost = current.cost() + renderingWeight(edge.kind());
                if (nextCost > bestCost.getOrDefault(edge.fromId(), Integer.MAX_VALUE)) continue;
                bestCost.put(edge.fromId(), nextCost);
                var path = new ArrayList<>(current.componentIds());
                path.add(edge.fromId());
                queue.add(new PathResult(List.copyOf(path), nextCost));
            }
        }
        return null;
    }

    private Integer renderingWeight(GitLabFrontendReachabilityEdgeKind kind) {
        return switch (kind) {
            case TEMPLATE_CHILD, ROUTED_CHILD -> 1;
            case DYNAMIC_COMPONENT -> 3;
            case COMPONENT_REFERENCE, USES_DEPENDENCY, DEPENDENCY_CALL -> null;
        };
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
            SourceSelection selection,
            Map<String, PackFile> files
    ) {
        var available = files.values().stream().filter(PackFile::available).count();
        var builder = new StringBuilder();
        builder.append("schema: ").append(SCHEMA).append('\n');
        builder.append("version: ").append(VERSION).append('\n');
        builder.append("semantics: STATIC_SCREEN_REACHABILITY_NOT_RUNTIME_ANCESTRY\n");
        builder.append("ordering: graph depth ascending, then breadth-first discovery order\n");
        builder.append("fullSourceStrategy: ").append(selection.strategy()).append('\n');
        builder.append("componentCount: ").append(components.size()).append('\n');
        builder.append("fullSourceComponentCount: ").append(selection.componentIds().size()).append('\n');
        builder.append("indexOnlyComponentCount: ").append(components.size() - selection.componentIds().size()).append('\n');
        builder.append("uniqueFileCount: ").append(files.size()).append('\n');
        builder.append("availableFileCount: ").append(available).append('\n');
        builder.append("unavailableFileCount: ").append(files.size() - available).append('\n');
        if (context != null && context.sourceRevision() != null) {
            builder.append("pinnedCommit: ").append(context.sourceRevision().revision()).append('\n');
        }
        var fullSourceComponents = components.stream()
                .filter(component -> selection.componentIds().contains(component.componentId())).toList();
        var complete = !fullSourceComponents.isEmpty()
                && selection.paths().stream().allMatch(SelectedPath::complete)
                && fullSourceComponents.stream().allMatch(component ->
                    StringUtils.hasText(component.sourcePath())
                            && available(files, component.sourcePath())
                            && (!StringUtils.hasText(component.templatePath())
                            || available(files, component.templatePath())));
        builder.append("complete: ").append(complete).append('\n');

        builder.append("\n## Selected full-source paths\n");
        if (selection.paths().isEmpty()) builder.append("- none\n");
        selection.paths().forEach(path -> builder.append("- candidate=").append(path.candidateId())
                .append(" complete=").append(path.complete()).append(" cost=").append(path.cost())
                .append(" targetToView=").append(String.join(" -> ", path.componentIds())).append('\n'));

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
            renderComponent(builder, index + 1, components.get(index), context, selection.componentIds(), files);
        }

        builder.append("\n## Unresolved discovery information\n");
        var unresolved = unresolved(context, components, selection);
        if (unresolved.isEmpty()) builder.append("- none reported by static discovery\n");
        else unresolved.forEach(value -> builder.append("- ").append(value).append('\n'));

        builder.append("\n## Full verified files for selected paths\n");
        if (files.isEmpty()) builder.append("- none selected or discovered\n");
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
            Set<String> fullSourceComponentIds,
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
        var fullSource = fullSourceComponentIds.contains(component.componentId());
        builder.append("sourceMode: ").append(fullSource ? "FULL_SOURCE" : "INDEX_ONLY").append('\n');
        builder.append("sourceFile: ").append(fileStatus(component.sourcePath(), files, fullSource)).append('\n');
        builder.append("templateFile: ").append(StringUtils.hasText(component.templatePath())
                ? fileStatus(component.templatePath(), files, fullSource) : "INLINE_OR_NOT_DISCOVERED").append('\n');
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
            List<GitLabFrontendReachabilityComponent> components,
            SourceSelection selection
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
        selection.limitations().forEach(value -> values.add("SOURCE_SELECTION_LIMITATION: " + value));
        return values.stream().distinct().toList();
    }

    private String fileStatus(String path, Map<String, PackFile> files, boolean fullSource) {
        if (!StringUtils.hasText(path)) return "NOT_DISCOVERED";
        if (!fullSource) return normalizedPath(path) + " [INDEX_ONLY]";
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

    private String normalizedPath(String path) {
        return StringUtils.hasText(path) ? path.trim().replace('\\', '/') : null;
    }

    private record SourceSelection(
            String strategy,
            Set<String> componentIds,
            List<SelectedPath> paths,
            List<String> limitations
    ) {}

    private record SelectedPath(String candidateId, List<String> componentIds, int cost, boolean complete) {}

    private record PathResult(List<String> componentIds, int cost) {}

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
