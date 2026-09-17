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
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependency;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdgeKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteConfiguration;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds a best-effort, pinned source pack limited to selected target-to-view
 * paths. The wider static graph remains available through repository tools but
 * is not serialized into the initial model context.
 */
@Service
@RequiredArgsConstructor
public class UxInspectorComponentSourcePackArtifactService {
    static final String SCHEMA = "tdw.ux-inspector-component-source-pack";
    static final int VERSION = 3;
    private static final int MAX_INHERITED_SLICE_CHARACTERS = 12_000;
    private static final String INHERITED_SLICE_BOUNDARY_MARKER =
            "\n// ... direct inherited slice bounded by UX Inspector ...";

    private final GitLabRepositoryPort repositoryPort;

    public UxInspectorComponentSourcePackArtifact prepare(
            UxInspectorTargetContext context,
            UxInspectorCapture capture
    ) {
        var components = orderedComponents(context);
        var selection = selectFullSourceComponents(context, components);
        var focusedComponents = components.stream()
                .filter(component -> selection.componentIds().contains(component.componentId()))
                .toList();
        var files = readFiles(context, focusedComponents);
        var viewComponent = findViewComponent(context, components);
        var inheritedSlice = directViewInheritanceSlice(context, viewComponent, components);
        var availablePaths = new LinkedHashSet<String>();
        files.values().stream().filter(PackFile::available).map(PackFile::path).forEach(availablePaths::add);
        routeSourcePaths(context).forEach(availablePaths::add);
        if (inheritedSlice != null && inheritedSlice.available()
                && StringUtils.hasText(inheritedSlice.sourcePath())) {
            availablePaths.add(inheritedSlice.sourcePath());
        }
        var availableCount = (int) files.values().stream().filter(PackFile::available).count();
        return new UxInspectorComponentSourcePackArtifact(
                render(context, capture, components, selection, files, inheritedSlice),
                components.size(), focusedComponents.size(), components.size() - focusedComponents.size(),
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
            Map<String, PackFile> files,
            InheritedSlice inheritedSlice
    ) {
        var available = files.values().stream().filter(PackFile::available).count();
        var focusedComponents = components.stream()
                .filter(component -> selection.componentIds().contains(component.componentId()))
                .toList();
        var builder = new StringBuilder();
        builder.append("schema: ").append(SCHEMA).append('\n');
        builder.append("version: ").append(VERSION).append('\n');
        builder.append("semantics: STATIC_SCREEN_REACHABILITY_NOT_RUNTIME_ANCESTRY\n");
        builder.append("scope: SELECTED_TARGET_TO_VIEW_PATHS_ONLY\n");
        builder.append("ordering: selected components in graph depth and breadth-first discovery order\n");
        builder.append("fullSourceStrategy: ").append(selection.strategy()).append('\n');
        builder.append("graphComponentCount: ").append(components.size()).append('\n');
        builder.append("focusedComponentCount: ").append(focusedComponents.size()).append('\n');
        builder.append("omittedGraphComponentCount: ").append(components.size() - focusedComponents.size()).append('\n');
        builder.append("uniqueFileCount: ").append(files.size()).append('\n');
        builder.append("availableFileCount: ").append(available).append('\n');
        builder.append("unavailableFileCount: ").append(files.size() - available).append('\n');
        if (context != null && context.sourceRevision() != null) {
            builder.append("pinnedCommit: ").append(context.sourceRevision().revision()).append('\n');
        }
        var complete = !focusedComponents.isEmpty()
                && selection.paths().stream().allMatch(SelectedPath::complete)
                && focusedComponents.stream().allMatch(component ->
                    StringUtils.hasText(component.sourcePath())
                            && available(files, component.sourcePath())
                            && (!StringUtils.hasText(component.templatePath())
                            || available(files, component.templatePath())));
        builder.append("complete: ").append(complete).append('\n');

        renderRouteContext(builder, context);

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

        renderComponentIndex(builder, focusedComponents, files);
        renderComponentRelations(builder, context, focusedComponents, selection.componentIds());
        renderInheritedSlice(builder, inheritedSlice);

        builder.append("\n## Unresolved discovery information\n");
        var unresolved = unresolved(context, focusedComponents, selection);
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

    private void renderRouteContext(StringBuilder builder, UxInspectorTargetContext context) {
        builder.append("\n## Effective route context\n");
        builder.append("semantics: DETERMINISTIC_PINNED_ROUTE_CHAIN_FOR_SELECTED_VIEW\n");
        if (context == null || context.graph() == null || context.graph().effectiveRouteChain() == null) {
            builder.append("- unavailable\n");
            return;
        }
        var chain = context.graph().effectiveRouteChain();
        builder.append("routeSegmentCount: ").append(chain.segments().size()).append('\n');
        builder.append("routeParameters: ").append(chain.routeParameters().isEmpty()
                ? "-" : String.join(",", chain.routeParameters())).append('\n');
        for (var index = 0; index < chain.segments().size(); index++) {
            var segment = chain.segments().get(index);
            builder.append("- order=").append(index + 1)
                    .append(" route=").append(lineText(segment.routePattern()))
                    .append(" pathSegment=").append(lineText(segment.pathSegment()))
                    .append(" outlet=").append(lineText(segment.outlet()))
                    .append(" source=").append(sourceReference(segment.source()));
            if (!segment.configuration().isEmpty()) {
                builder.append(" configuration=")
                        .append(segment.configuration().stream().map(this::routeConfiguration)
                                .collect(java.util.stream.Collectors.joining(";")));
            }
            builder.append('\n');
        }
        builder.append("Research rule: treat this route chain as complete initial evidence for the selected view; ")
                .append("read route source only when an exact route-body detail absent above is material.\n");
    }

    private String routeConfiguration(GitLabFrontendRouteConfiguration configuration) {
        var detail = new ArrayList<String>();
        detail.add(configuration.kind().name());
        if (StringUtils.hasText(configuration.key())) detail.add("key=" + lineText(configuration.key()));
        if (!configuration.referencedSymbols().isEmpty()) {
            detail.add("symbols=" + String.join(",", configuration.referencedSymbols()));
        }
        if (StringUtils.hasText(configuration.staticValue())) {
            detail.add("value=" + lineText(configuration.staticValue()));
        }
        detail.add("status=" + configuration.status().name());
        return "[" + String.join(",", detail) + "]";
    }

    private String sourceReference(GitLabFrontendSourceReference source) {
        if (source == null || !StringUtils.hasText(source.path())) return "-";
        var result = new StringBuilder(lineText(source.path()));
        if (source.startLine() != null) {
            result.append("#L").append(source.startLine());
            if (source.endLine() != null && !source.endLine().equals(source.startLine())) {
                result.append("-L").append(source.endLine());
            }
        }
        return result.toString();
    }

    private List<String> routeSourcePaths(UxInspectorTargetContext context) {
        if (context == null || context.graph() == null || context.graph().effectiveRouteChain() == null) {
            return List.of();
        }
        return context.graph().effectiveRouteChain().segments().stream()
                .map(segment -> segment.source() != null ? normalizedPath(segment.source().path()) : null)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private InheritedSlice directViewInheritanceSlice(
            UxInspectorTargetContext context,
            GitLabFrontendReachabilityComponent viewComponent,
            List<GitLabFrontendReachabilityComponent> components
    ) {
        if (context == null || context.graph() == null || viewComponent == null) return null;
        var dependency = context.graph().dependencies().stream()
                .filter(candidate -> candidate.kind() == GitLabFrontendReachabilityDependencyKind.INHERITED_TYPE)
                .filter(candidate -> candidate.usedBy().contains(viewComponent.componentId()))
                .sorted(Comparator.comparingInt(GitLabFrontendReachabilityDependency::discoveryOrder)
                        .thenComparing(GitLabFrontendReachabilityDependency::dependencyId))
                .findFirst().orElse(null);
        if (dependency != null) return inheritedDependencySlice(viewComponent, dependency);

        var componentById = components.stream().collect(java.util.stream.Collectors.toMap(
                GitLabFrontendReachabilityComponent::componentId,
                component -> component,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        return context.graph().edges().stream()
                .filter(edge -> edge.kind() == GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE)
                .filter(edge -> viewComponent.componentId().equals(edge.fromId()))
                .filter(edge -> "INHERITED_MEMBER".equals(edge.label()))
                .sorted(Comparator.comparing(GitLabFrontendReachabilityEdge::toId))
                .map(edge -> inheritedComponentSlice(viewComponent, componentById.get(edge.toId())))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private InheritedSlice inheritedDependencySlice(
            GitLabFrontendReachabilityComponent viewComponent,
            GitLabFrontendReachabilityDependency dependency
    ) {
        var content = boundedSlice(dependency.sliceContent());
        var limitations = new ArrayList<>(dependency.limitations());
        if (dependency.sliceContent().length() > content.length()) {
            limitations.add("Direct inherited slice was bounded to " + MAX_INHERITED_SLICE_CHARACTERS + " characters.");
        }
        return new InheritedSlice(
                viewComponent.componentId(), dependency.symbol(), dependency.sourcePath(), dependency.status(),
                dependency.methods(), content, dependency.sourceCharacters(), content.length(),
                dependency.truncated() || dependency.sliceContent().length() > content.length(),
                List.copyOf(limitations)
        );
    }

    private InheritedSlice inheritedComponentSlice(
            GitLabFrontendReachabilityComponent viewComponent,
            GitLabFrontendReachabilityComponent baseComponent
    ) {
        if (baseComponent == null) return null;
        var content = boundedSlice(baseComponent.sliceContent());
        var limitations = new ArrayList<>(baseComponent.limitations());
        if (baseComponent.sliceContent().length() > content.length()) {
            limitations.add("Direct inherited component slice was bounded to "
                    + MAX_INHERITED_SLICE_CHARACTERS + " characters.");
        }
        return new InheritedSlice(
                viewComponent.componentId(), baseComponent.symbol(), baseComponent.sourcePath(), baseComponent.status(),
                baseComponent.includedSymbols().stream().map(symbol -> symbol.symbolName()).toList(),
                content, baseComponent.sourceCharacters(), content.length(),
                baseComponent.truncated() || baseComponent.sliceContent().length() > content.length(),
                List.copyOf(limitations)
        );
    }

    private String boundedSlice(String content) {
        if (!StringUtils.hasText(content)) return "";
        if (content.length() <= MAX_INHERITED_SLICE_CHARACTERS) return content;
        var retainedCharacters = MAX_INHERITED_SLICE_CHARACTERS - INHERITED_SLICE_BOUNDARY_MARKER.length();
        return content.substring(0, retainedCharacters) + INHERITED_SLICE_BOUNDARY_MARKER;
    }

    private void renderInheritedSlice(StringBuilder builder, InheritedSlice inheritedSlice) {
        builder.append("\n## Direct view inheritance slice\n");
        builder.append("maxDepth: 1\n");
        if (inheritedSlice == null) {
            builder.append("- none discovered for the selected view component\n");
            return;
        }
        builder.append("ownerComponentId: ").append(lineText(inheritedSlice.ownerComponentId())).append('\n');
        builder.append("baseSymbol: ").append(lineText(inheritedSlice.symbol())).append('\n');
        builder.append("sourcePath: ").append(lineText(inheritedSlice.sourcePath())).append('\n');
        builder.append("sourceMode: ").append(inheritedSlice.available() ? "AVAILABLE_SLICE" : "UNAVAILABLE").append('\n');
        builder.append("status: ").append(lineText(inheritedSlice.status())).append('\n');
        builder.append("members: ").append(inheritedSlice.members().isEmpty()
                ? "-" : String.join(",", inheritedSlice.members())).append('\n');
        builder.append("sourceCharacters: ").append(inheritedSlice.sourceCharacters()).append('\n');
        builder.append("returnedCharacters: ").append(inheritedSlice.returnedCharacters()).append('\n');
        builder.append("truncated: ").append(inheritedSlice.truncated()).append('\n');
        if (!inheritedSlice.limitations().isEmpty()) {
            builder.append("limitations:\n");
            inheritedSlice.limitations().forEach(limitation -> builder.append("- ").append(lineText(limitation)).append('\n'));
        }
        if (inheritedSlice.available()) {
            builder.append("content:\nBEGIN_UNTRUSTED_INHERITED_SLICE ")
                    .append(inheritedSlice.sourcePath()).append('\n');
            builder.append(inheritedSlice.content());
            if (!inheritedSlice.content().endsWith("\n")) builder.append('\n');
            builder.append("END_UNTRUSTED_INHERITED_SLICE ").append(inheritedSlice.sourcePath()).append('\n');
        }
        builder.append("Research rule: do not read this base source again unless the slice is truncated, unavailable, ")
                .append("or a material inherited member is absent.\n");
    }

    private String lineText(String value) {
        return value != null ? value.replace("\r", " ").replace("\n", " ").trim() : "-";
    }

    private void renderComponentIndex(
            StringBuilder builder,
            List<GitLabFrontendReachabilityComponent> components,
            Map<String, PackFile> files
    ) {
        builder.append("\n## Focused path components\n");
        if (components.isEmpty()) {
            builder.append("- none selected from the static graph\n");
            return;
        }
        builder.append("| # | componentId | depth | bfs | symbol | selector | discovery | status | sourceMode | sourceFile | templateFile | truncated | limitations |\n");
        builder.append("|---:|---|---:|---:|---|---|---|---|---|---|---|---|---|\n");
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            builder.append("| ").append(index + 1)
                    .append(" | ").append(tableText(component.componentId()))
                    .append(" | ").append(component.depth())
                    .append(" | ").append(component.breadthFirstOrder())
                    .append(" | ").append(tableText(component.symbol()))
                    .append(" | ").append(tableText(component.selector()))
                    .append(" | ").append(tableText(component.discoveryKind()))
                    .append(" | ").append(tableText(component.status()))
                    .append(" | FULL_SOURCE")
                    .append(" | ").append(tableText(fileStatus(component.sourcePath(), files)))
                    .append(" | ").append(tableText(StringUtils.hasText(component.templatePath())
                            ? fileStatus(component.templatePath(), files) : "INLINE_OR_NOT_DISCOVERED"))
                    .append(" | ").append(component.truncated())
                    .append(" | ").append(tableText(component.limitations().isEmpty()
                            ? "-" : String.join("; ", component.limitations())))
                    .append(" |\n");
        }
    }

    private void renderComponentRelations(
            StringBuilder builder,
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components,
            Set<String> focusedComponentIds
    ) {
        var relations = componentRelations(context, components, focusedComponentIds);
        builder.append("\n## Focused component relations\n");
        builder.append("relationCount: ").append(relations.size()).append('\n');
        if (relations.isEmpty()) {
            builder.append("- none reported by static discovery\n");
            return;
        }
        for (var relation : relations) {
            builder.append("- ").append(relation.fromId())
                    .append(" --").append(relation.kind()).append("--> ")
                    .append(relation.toId()).append('\n');
        }
    }

    private List<ComponentRelation> componentRelations(
            UxInspectorTargetContext context,
            List<GitLabFrontendReachabilityComponent> components,
            Set<String> focusedComponentIds
    ) {
        var relations = new LinkedHashMap<String, ComponentRelation>();
        var graphEdges = context != null && context.graph() != null
                ? context.graph().edges() : List.<GitLabFrontendReachabilityEdge>of();
        graphEdges.stream()
                .filter(edge -> edge != null && StringUtils.hasText(edge.fromId())
                        && StringUtils.hasText(edge.toId()) && edge.kind() != null)
                .filter(edge -> focusedComponentIds.contains(edge.fromId())
                        && focusedComponentIds.contains(edge.toId()))
                .map(edge -> new ComponentRelation(edge.fromId(), edge.kind().name(), edge.toId()))
                .sorted(ComponentRelation.ORDER)
                .forEach(relation -> relations.putIfAbsent(relation.key(), relation));

        var representedPairs = relations.values().stream()
                .map(ComponentRelation::pairKey)
                .collect(java.util.stream.Collectors.toSet());
        for (var component : components) {
            component.childComponentIds().stream().filter(StringUtils::hasText).distinct().sorted()
                    .map(childId -> new ComponentRelation(
                            component.componentId(), "DECLARED_CHILD", childId))
                    .filter(relation -> focusedComponentIds.contains(relation.toId()))
                    .filter(relation -> !representedPairs.contains(relation.pairKey()))
                    .forEach(relation -> relations.putIfAbsent(relation.key(), relation));
            component.dependencyIds().stream().filter(StringUtils::hasText).distinct().sorted()
                    .map(dependencyId -> new ComponentRelation(
                            component.componentId(), "DECLARED_DEPENDENCY", dependencyId))
                    .filter(relation -> focusedComponentIds.contains(relation.toId()))
                    .filter(relation -> !representedPairs.contains(relation.pairKey()))
                    .forEach(relation -> relations.putIfAbsent(relation.key(), relation));
        }
        return relations.values().stream().sorted(ComponentRelation.ORDER).toList();
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

    private String tableText(String value) {
        return text(value).replace("|", "\\|");
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

    private record InheritedSlice(
            String ownerComponentId,
            String symbol,
            String sourcePath,
            String status,
            List<String> members,
            String content,
            int sourceCharacters,
            int returnedCharacters,
            boolean truncated,
            List<String> limitations
    ) {
        private InheritedSlice {
            members = members != null ? List.copyOf(members) : List.of();
            content = content != null ? content : "";
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }

        private boolean available() {
            return StringUtils.hasText(sourcePath) && StringUtils.hasText(content);
        }
    }

    private record ComponentRelation(String fromId, String kind, String toId) {
        private static final Comparator<ComponentRelation> ORDER = Comparator
                .comparing(ComponentRelation::fromId)
                .thenComparing(ComponentRelation::kind)
                .thenComparing(ComponentRelation::toId);

        private String key() {
            return fromId + '\u0000' + kind + '\u0000' + toId;
        }

        private String pairKey() {
            return fromId + '\u0000' + toId;
        }
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
