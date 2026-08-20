package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabFrontendScreenReachabilityService {

    private static final Pattern COMPONENT_DECORATOR = Pattern.compile("@Component\\s*\\(");
    private static final Pattern COMPONENT_CLASS = Pattern.compile(
            "(?s)(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern SELECTOR = Pattern.compile("selector\\s*:\\s*(['\"])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern TEMPLATE_URL = Pattern.compile("templateUrl\\s*:\\s*(['\"])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern INLINE_TEMPLATE = Pattern.compile("template\\s*:\\s*([`'\"])");
    private static final Pattern DECLARED_SYMBOL = Pattern.compile(
            "(?m)\\b(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?"
                    + "(?:class|interface|enum|function|const)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern ELEMENT_TAG = Pattern.compile("<\\s*([A-Za-z][A-Za-z0-9_.:-]*)");

    private final GitLabFrontendScreenGraphContextService screenGraphContextService;
    private final GitLabTypeScriptSymbolSliceService typeScriptSymbolSliceService;

    public GitLabFrontendScreenReachabilityGraph build(GitLabFrontendScreenGraphContextRequest request) {
        var context = screenGraphContextService.build(request);
        var sourceIndex = new SourceIndex(context.sourceFiles());
        var descriptors = componentDescriptors(context, sourceIndex);
        var root = rootDescriptor(context, descriptors);
        var relations = componentRelations(descriptors);
        var edgeIndex = new LinkedHashMap<String, GitLabFrontendReachabilityEdge>();
        relations.values().stream().flatMap(List::stream).forEach(edge -> addEdge(edgeIndex, edge));

        var breadthFirst = breadthFirst(root, descriptors, relations);
        var connectedIds = breadthFirst.stream().map(ComponentPosition::descriptor)
                .map(ComponentDescriptor::componentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var unlinked = descriptors.stream()
                .filter(descriptor -> !connectedIds.contains(descriptor.componentId()))
                .toList();

        var orderedPositions = new ArrayList<>(breadthFirst);
        var nextOrder = orderedPositions.size();
        for (var descriptor : unlinked) {
            orderedPositions.add(new ComponentPosition(descriptor, -1, nextOrder++, false, "UNLINKED"));
        }

        var components = new LinkedHashMap<String, MutableComponent>();
        var dependencies = new LinkedHashMap<String, MutableDependency>();
        var dependencyQueue = new ArrayDeque<String>();
        var queuedDependencies = new LinkedHashSet<String>();
        for (var position : orderedPositions) {
            var slice = componentSlice(request.scope(), position.descriptor());
            var component = new MutableComponent(position, slice);
            components.put(position.descriptor().componentId(), component);
            collectDependencies(
                    request.scope(), component.id(), component.descriptor.sourcePath(), slice.downstreamReferences(),
                    sourceIndex, descriptors, dependencies, dependencyQueue, queuedDependencies, edgeIndex,
                    component.dependencyIds
            );
        }

        processDependencies(
                request.scope(), sourceIndex, descriptors, dependencies,
                dependencyQueue, queuedDependencies, edgeIndex
        );

        for (var component : components.values()) {
            component.childIds.addAll(relations.getOrDefault(component.id(), List.of()).stream()
                    .map(GitLabFrontendReachabilityEdge::toId)
                    .distinct()
                    .toList());
        }

        var connectedComponents = components.values().stream()
                .filter(component -> component.position.connected())
                .map(MutableComponent::toResponse)
                .toList();
        var levels = connectedComponents.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GitLabFrontendReachabilityComponent::depth,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> new GitLabFrontendReachabilityComponentLevel(entry.getKey(), entry.getValue()))
                .toList();
        var unlinkedComponents = components.values().stream()
                .filter(component -> !component.position.connected())
                .map(MutableComponent::toResponse)
                .toList();
        var dependencyResponses = dependencies.values().stream()
                .sorted(Comparator.comparingInt(dependency -> dependency.order))
                .map(MutableDependency::toResponse)
                .toList();
        var edges = List.copyOf(edgeIndex.values());
        var limitations = limitations(context, root, unlinkedComponents, components, dependencies);
        var partial = root == null || context.contextLimitReached() || context.graphCoverage().limitReached()
                || !unlinkedComponents.isEmpty()
                || components.values().stream().anyMatch(MutableComponent::partial)
                || dependencies.values().stream().anyMatch(MutableDependency::partial);
        var status = root == null ? "BLOCKED" : partial ? "PARTIAL" : "OK";
        var outline = readableOutline(
                status, context, levels, unlinkedComponents, dependencyResponses, edges, limitations
        );
        var sliceCharacters = components.values().stream().mapToInt(component -> component.slice.returnedCharacters()).sum()
                + dependencies.values().stream().mapToInt(dependency -> dependency.returnedCharacters).sum();
        return new GitLabFrontendScreenReachabilityGraph(
                context.scope(), context.sourceRevision(), status, context.screenNode(), context.effectiveRouteChain(),
                levels, unlinkedComponents, dependencyResponses, edges, context.technicalSignals(), context.diagnostics(),
                context.sourceFiles().size(), context.totalReturnedCharacters(), sliceCharacters, outline.length(),
                context.contextLimitReached() || context.graphCoverage().limitReached(), limitations, outline
        );
    }

    private List<ComponentDescriptor> componentDescriptors(
            GitLabFrontendScreenGraphContext context,
            SourceIndex sourceIndex
    ) {
        var result = new ArrayList<ComponentDescriptor>();
        var sourceOrder = 0;
        for (var file : context.sourceFiles()) {
            if (!file.path().endsWith(".ts") && !file.path().endsWith(".tsx")) {
                continue;
            }
            var matcher = COMPONENT_DECORATOR.matcher(file.content());
            while (matcher.find()) {
                var open = file.content().indexOf('(', matcher.start());
                var close = matching(file.content(), open, '(', ')');
                if (close < 0) {
                    continue;
                }
                var classMatcher = COMPONENT_CLASS.matcher(file.content());
                classMatcher.region(close + 1, file.content().length());
                if (!classMatcher.find()) {
                    continue;
                }
                var metadata = file.content().substring(open + 1, close);
                var symbol = classMatcher.group(1);
                var selectors = selectorValues(metadata);
                var templatePath = templatePath(file.path(), metadata);
                var template = StringUtils.hasText(templatePath)
                        ? sourceIndex.content(templatePath)
                        : inlineTemplate(metadata);
                result.add(new ComponentDescriptor(
                        stableId("component", file.path() + "#" + symbol), sourceOrder++, symbol,
                        selectors, file.path(), templatePath, template, file.content()
                ));
            }
        }
        return List.copyOf(result);
    }

    private ComponentDescriptor rootDescriptor(
            GitLabFrontendScreenGraphContext context,
            List<ComponentDescriptor> descriptors
    ) {
        var target = context.screenNode().viewTarget();
        if (target == null) {
            return null;
        }
        return descriptors.stream()
                .filter(descriptor -> descriptor.sourcePath().equals(target.sourcePath()))
                .filter(descriptor -> !StringUtils.hasText(target.symbol()) || descriptor.symbol().equals(target.symbol()))
                .findFirst()
                .orElseGet(() -> descriptors.stream()
                        .filter(descriptor -> descriptor.sourcePath().equals(target.sourcePath()))
                        .findFirst().orElse(null));
    }

    private Map<String, List<GitLabFrontendReachabilityEdge>> componentRelations(
            List<ComponentDescriptor> descriptors
    ) {
        var result = new LinkedHashMap<String, List<GitLabFrontendReachabilityEdge>>();
        for (var parent : descriptors) {
            var edges = new ArrayList<GitLabFrontendReachabilityEdge>();
            for (var child : descriptors) {
                if (parent.componentId().equals(child.componentId())) {
                    continue;
                }
                var matchedSelector = child.selectors().stream()
                        .filter(selector -> templateUses(parent.template(), selector))
                        .findFirst().orElse(null);
                if (matchedSelector != null) {
                    edges.add(new GitLabFrontendReachabilityEdge(
                            parent.componentId(), child.componentId(),
                            GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD, matchedSelector,
                            parent.templatePath(), parent.symbol(), child.symbol()
                    ));
                } else if (dynamicallyUses(parent.source(), child.symbol())) {
                    edges.add(new GitLabFrontendReachabilityEdge(
                            parent.componentId(), child.componentId(),
                            GitLabFrontendReachabilityEdgeKind.DYNAMIC_COMPONENT, child.symbol(),
                            parent.sourcePath(), parent.symbol(), child.symbol()
                    ));
                }
            }
            if (!edges.isEmpty()) {
                result.put(parent.componentId(), List.copyOf(edges));
            }
        }
        return result;
    }

    private List<ComponentPosition> breadthFirst(
            ComponentDescriptor root,
            List<ComponentDescriptor> descriptors,
            Map<String, List<GitLabFrontendReachabilityEdge>> relations
    ) {
        if (root == null) {
            return List.of();
        }
        var byId = descriptors.stream().collect(java.util.stream.Collectors.toMap(
                ComponentDescriptor::componentId, descriptor -> descriptor, (left, right) -> left, LinkedHashMap::new
        ));
        var result = new ArrayList<ComponentPosition>();
        var queue = new ArrayDeque<ComponentDepth>();
        var visited = new LinkedHashSet<String>();
        queue.add(new ComponentDepth(root.componentId(), 0, "SELECTED_SCREEN"));
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!visited.add(current.componentId())) {
                continue;
            }
            var descriptor = byId.get(current.componentId());
            if (descriptor == null) {
                continue;
            }
            result.add(new ComponentPosition(descriptor, current.depth(), result.size(), true, current.discoveryKind()));
            for (var edge : relations.getOrDefault(current.componentId(), List.of())) {
                queue.add(new ComponentDepth(edge.toId(), current.depth() + 1, edge.kind().name()));
            }
        }
        return List.copyOf(result);
    }

    private GitLabTypeScriptSymbolSliceResponse componentSlice(
            GitLabFrontendRepositoryScope scope,
            ComponentDescriptor descriptor
    ) {
        return typeScriptSymbolSliceService.readSymbolSlice(new GitLabTypeScriptSymbolSliceRequest(
                scope, descriptor.sourcePath(), descriptor.symbol(), descriptor.templatePath(), true, List.of(),
                true, true, true, GitLabTypeScriptSymbolSliceService.MAX_OUTPUT_CHARACTERS
        ));
    }

    private void collectDependencies(
            GitLabFrontendRepositoryScope scope,
            String ownerId,
            String ownerPath,
            List<GitLabTypeScriptDownstreamReference> references,
            SourceIndex sourceIndex,
            List<ComponentDescriptor> descriptors,
            LinkedHashMap<String, MutableDependency> dependencies,
            ArrayDeque<String> queue,
            Set<String> queued,
            LinkedHashMap<String, GitLabFrontendReachabilityEdge> edges,
            LinkedHashSet<String> ownerDependencyIds
    ) {
        for (var reference : references) {
            if (!materialDependency(reference)) {
                continue;
            }
            var symbol = StringUtils.hasText(reference.targetSymbol())
                    ? reference.targetSymbol() : reference.ownerSymbol();
            var targetPath = sourceIndex.resolve(ownerPath, reference.moduleSpecifier(), symbol);
            var componentTarget = descriptors.stream()
                    .filter(descriptor -> descriptor.sourcePath().equals(targetPath))
                    .filter(descriptor -> descriptor.symbol().equals(symbol))
                    .findFirst().orElse(null);
            if (componentTarget != null) {
                addEdge(edges, new GitLabFrontendReachabilityEdge(
                        ownerId, componentTarget.componentId(), GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE,
                        reference.kind().name(), ownerPath, reference.sourceSymbol(), reference.memberSymbol()
                ));
                continue;
            }
            var dependencyId = stableId(
                    "dependency",
                    (StringUtils.hasText(targetPath) ? targetPath : value(reference.moduleSpecifier())) + "#" + value(symbol)
            );
            var dependency = dependencies.computeIfAbsent(dependencyId, ignored -> new MutableDependency(
                    dependencyId, dependencies.size(), dependencyKind(reference, symbol), symbol,
                    targetPath, reference.moduleSpecifier()
            ));
            dependency.usedBy.add(ownerId);
            if (StringUtils.hasText(reference.memberSymbol())) {
                dependency.methods.add(reference.memberSymbol());
            }
            ownerDependencyIds.add(dependencyId);
            addEdge(edges, new GitLabFrontendReachabilityEdge(
                    ownerId, dependencyId,
                    ownerId.startsWith("dependency-")
                            ? GitLabFrontendReachabilityEdgeKind.DEPENDENCY_CALL
                            : GitLabFrontendReachabilityEdgeKind.USES_DEPENDENCY,
                    reference.kind().name(), ownerPath, reference.sourceSymbol(), reference.memberSymbol()
            ));
            if (StringUtils.hasText(targetPath) && queued.add(dependencyId)) {
                queue.add(dependencyId);
            }
        }
    }

    private void processDependencies(
            GitLabFrontendRepositoryScope scope,
            SourceIndex sourceIndex,
            List<ComponentDescriptor> descriptors,
            LinkedHashMap<String, MutableDependency> dependencies,
            ArrayDeque<String> queue,
            Set<String> queued,
            LinkedHashMap<String, GitLabFrontendReachabilityEdge> edges
    ) {
        while (!queue.isEmpty()) {
            var dependencyId = queue.removeFirst();
            queued.remove(dependencyId);
            var dependency = dependencies.get(dependencyId);
            if (dependency == null || !StringUtils.hasText(dependency.sourcePath)
                    || dependency.methods.equals(dependency.processedMethods)) {
                continue;
            }
            var selectors = dependency.methods.stream()
                    .map(method -> new GitLabTypeScriptSymbolSelector(method, GitLabTypeScriptSymbolKind.AUTO, null))
                    .toList();
            var declaringType = sourceIndex.declaresClass(dependency.sourcePath, dependency.symbol)
                    ? dependency.symbol : null;
            var slice = typeScriptSymbolSliceService.readSymbolSlice(new GitLabTypeScriptSymbolSliceRequest(
                    scope, dependency.sourcePath, declaringType, null, false, selectors,
                    true, true, true, GitLabTypeScriptSymbolSliceService.MAX_OUTPUT_CHARACTERS
            ));
            dependency.update(slice);
            dependency.processedMethods.clear();
            dependency.processedMethods.addAll(dependency.methods);
            var downstream = new LinkedHashSet<String>();
            collectDependencies(
                    scope, dependencyId, dependency.sourcePath, slice.downstreamReferences(), sourceIndex, descriptors,
                    dependencies, queue, queued, edges, downstream
            );
            dependency.downstreamDependencyIds.addAll(downstream);
        }
    }

    private List<String> limitations(
            GitLabFrontendScreenGraphContext context,
            ComponentDescriptor root,
            List<GitLabFrontendReachabilityComponent> unlinked,
            Map<String, MutableComponent> components,
            Map<String, MutableDependency> dependencies
    ) {
        var result = new LinkedHashSet<String>();
        result.addAll(context.graphCoverage().limitations());
        if (root == null) {
            result.add("Selected screen component was not found among deterministically delivered component sources.");
        }
        if (context.contextLimitReached()) {
            result.add("Deterministic source delivery reached its context boundary before the BFS graph was rendered.");
        }
        if (!unlinked.isEmpty()) {
            result.add(unlinked.size() + " delivered component(s) have no confirmed parent edge from the selected screen.");
        }
        var partialComponents = components.values().stream().filter(MutableComponent::partial).count();
        if (partialComponents > 0) {
            result.add(partialComponents + " component slice(s) are partial or unresolved.");
        }
        var partialDependencies = dependencies.values().stream().filter(MutableDependency::partial).count();
        if (partialDependencies > 0) {
            result.add(partialDependencies + " repository dependency slice(s) are partial or unresolved.");
        }
        return List.copyOf(result);
    }

    private String readableOutline(
            String status,
            GitLabFrontendScreenGraphContext context,
            List<GitLabFrontendReachabilityComponentLevel> levels,
            List<GitLabFrontendReachabilityComponent> unlinked,
            List<GitLabFrontendReachabilityDependency> dependencies,
            List<GitLabFrontendReachabilityEdge> edges,
            List<String> limitations
    ) {
        var result = new StringBuilder();
        var componentReferences = new LinkedHashMap<String, String>();
        levels.stream().flatMap(level -> level.components().stream())
                .forEach(component -> componentReferences.put(
                        component.componentId(), "C" + (component.breadthFirstOrder() + 1) + " " + component.symbol()
                ));
        unlinked.forEach(component -> componentReferences.put(
                component.componentId(), "C" + (component.breadthFirstOrder() + 1) + " " + component.symbol()
        ));
        var dependencyReferences = new LinkedHashMap<String, String>();
        dependencies.forEach(dependency -> dependencyReferences.put(
                dependency.dependencyId(), "D" + (dependency.discoveryOrder() + 1) + " " + value(dependency.symbol())
        ));
        result.append("# Frontend screen reachability graph\n\n");
        result.append("- status: `").append(status).append("`\n");
        result.append("- screen: `").append(context.screenNode().routePattern()).append("` -> `")
                .append(context.screenNode().viewTarget() != null ? context.screenNode().viewTarget().symbol() : "unresolved")
                .append("`\n");
        result.append("- revision: `").append(value(context.sourceRevision().commitId())).append("`\n\n");
        result.append("## Effective route chain\n\n");
        var routeOrder = 1;
        for (var segment : context.effectiveRouteChain().segments()) {
            result.append(routeOrder++).append(". `").append(segment.routePattern()).append("` — `")
                    .append(segment.source().path()).append("`");
            var configuration = segment.configuration().stream()
                    .map(this::routeConfigurationLabel)
                    .toList();
            if (!configuration.isEmpty()) {
                result.append(" — ").append(String.join("; ", configuration));
            }
            result.append("\n");
        }
        result.append("\n## Component breadth-first traversal\n");
        for (var level : levels) {
            result.append("\n### Depth ").append(level.depth()).append("\n");
            for (var component : level.components()) {
                appendComponent(result, component, edges, componentReferences, dependencyReferences);
            }
        }
        if (!unlinked.isEmpty()) {
            result.append("\n## Delivered components without a confirmed parent edge\n");
            for (var component : unlinked) {
                appendComponent(result, component, edges, componentReferences, dependencyReferences);
            }
        }
        result.append("\n## Canonical dependencies\n");
        for (var dependency : dependencies) {
            result.append("\n### [D").append(dependency.discoveryOrder() + 1).append("] ")
                    .append(value(dependency.symbol())).append("\n");
            result.append("- kind: `").append(dependency.kind()).append("`\n");
            result.append("- source: `").append(StringUtils.hasText(dependency.sourcePath())
                    ? dependency.sourcePath() : value(dependency.moduleSpecifier())).append("`\n");
            result.append("- methods: ").append(joinOrNone(dependency.methods())).append("\n");
            result.append("- used by: ").append(joinOrNone(dependency.usedBy().stream()
                    .map(id -> componentReferences.getOrDefault(
                            id, dependencyReferences.getOrDefault(id, id)
                    )).toList())).append("\n");
            if (!dependency.downstreamDependencyIds().isEmpty()) {
                result.append("- downstream: ").append(dependency.downstreamDependencyIds().stream()
                        .map(id -> dependencyReferences.getOrDefault(id, id))
                        .collect(java.util.stream.Collectors.joining(", "))).append("\n");
            }
        }
        if (!limitations.isEmpty()) {
            result.append("\n## Explicit boundaries\n");
            limitations.forEach(limitation -> result.append("- ").append(limitation).append("\n"));
        }
        return result.toString().stripTrailing();
    }

    private void appendComponent(
            StringBuilder result,
            GitLabFrontendReachabilityComponent component,
            List<GitLabFrontendReachabilityEdge> edges,
            Map<String, String> componentReferences,
            Map<String, String> dependencyReferences
    ) {
        result.append("\n#### [C").append(component.breadthFirstOrder() + 1).append("] ")
                .append(component.symbol()).append("\n");
        result.append("- source: `").append(component.sourcePath()).append("`\n");
        if (StringUtils.hasText(component.selector())) {
            result.append("- selector: `").append(component.selector()).append("`\n");
        }
        result.append("- entry symbols: ").append(joinOrNone(component.entrySymbols().stream()
                .map(GitLabTypeScriptSymbolCandidate::symbolName).toList())).append("\n");
        result.append("- dependencies: ").append(joinOrNone(component.dependencyIds().stream()
                .map(id -> dependencyReferences.getOrDefault(id, id)).toList())).append("\n");
        var children = edges.stream()
                .filter(edge -> edge.fromId().equals(component.componentId()))
                .filter(edge -> edge.kind() == GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD
                        || edge.kind() == GitLabFrontendReachabilityEdgeKind.DYNAMIC_COMPONENT)
                .map(edge -> componentReferences.getOrDefault(edge.toId(), edge.toId())
                        + " (" + edge.kind() + ")")
                .toList();
        result.append("- children: ").append(joinOrNone(children)).append("\n");
    }

    private String routeConfigurationLabel(GitLabFrontendRouteConfiguration configuration) {
        var detail = !configuration.referencedSymbols().isEmpty()
                ? String.join(",", configuration.referencedSymbols())
                : StringUtils.hasText(configuration.staticValue())
                ? configuration.staticValue().replaceAll("\\s+", " ").trim()
                : configuration.status();
        return configuration.kind() + "=" + detail;
    }

    private boolean materialDependency(GitLabTypeScriptDownstreamReference reference) {
        if (reference == null) {
            return false;
        }
        if (reference.kind() == GitLabTypeScriptDownstreamReferenceKind.PROPERTY_ACCESS
                && !StringUtils.hasText(reference.targetSymbol())
                && !StringUtils.hasText(reference.moduleSpecifier())) {
            return false;
        }
        return StringUtils.hasText(reference.targetSymbol())
                || StringUtils.hasText(reference.moduleSpecifier())
                || reference.kind() == GitLabTypeScriptDownstreamReferenceKind.NGRX_DISPATCH
                || reference.kind() == GitLabTypeScriptDownstreamReferenceKind.NGRX_SELECT;
    }

    private GitLabFrontendReachabilityDependencyKind dependencyKind(
            GitLabTypeScriptDownstreamReference reference,
            String symbol
    ) {
        var module = value(reference.moduleSpecifier()).toLowerCase(Locale.ROOT);
        var type = value(symbol);
        if (reference.kind() == GitLabTypeScriptDownstreamReferenceKind.BACKEND_OPERATION) {
            return GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT;
        }
        if (reference.kind() == GitLabTypeScriptDownstreamReferenceKind.NGRX_ACTION
                || reference.kind() == GitLabTypeScriptDownstreamReferenceKind.NGRX_DISPATCH
                || reference.kind() == GitLabTypeScriptDownstreamReferenceKind.NGRX_SELECT
                || module.contains("@ngrx/")) {
            return GitLabFrontendReachabilityDependencyKind.NGRX;
        }
        if (reference.kind() == GitLabTypeScriptDownstreamReferenceKind.RXJS_PIPELINE
                || module.equals("rxjs") || module.startsWith("rxjs/")) {
            return GitLabFrontendReachabilityDependencyKind.RXJS;
        }
        if (module.contains("websocket") || type.toLowerCase(Locale.ROOT).contains("websocket")) {
            return GitLabFrontendReachabilityDependencyKind.WEBSOCKET;
        }
        if (type.endsWith("Facade")) {
            return GitLabFrontendReachabilityDependencyKind.FACADE;
        }
        if (type.matches(".*(?:Api|ApiService|Client|ControllerService)$")) {
            return GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT;
        }
        if (type.endsWith("Service")) {
            return GitLabFrontendReachabilityDependencyKind.SERVICE;
        }
        if (reference.kind() == GitLabTypeScriptDownstreamReferenceKind.IMPORTED_FUNCTION) {
            return GitLabFrontendReachabilityDependencyKind.IMPORTED_FUNCTION;
        }
        if (!StringUtils.hasText(reference.targetSourcePath()) && !StringUtils.hasText(type)) {
            return GitLabFrontendReachabilityDependencyKind.EXTERNAL;
        }
        return GitLabFrontendReachabilityDependencyKind.UNKNOWN;
    }

    private List<String> selectorValues(String metadata) {
        var matcher = SELECTOR.matcher(metadata);
        if (!matcher.find()) {
            return List.of();
        }
        return java.util.Arrays.stream(matcher.group(2).split(","))
                .map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String templatePath(String sourcePath, String metadata) {
        var matcher = TEMPLATE_URL.matcher(metadata);
        return matcher.find() ? relative(sourcePath, matcher.group(2).trim()) : null;
    }

    private String inlineTemplate(String metadata) {
        var matcher = INLINE_TEMPLATE.matcher(metadata);
        if (!matcher.find()) {
            return "";
        }
        var quote = matcher.group(1).charAt(0);
        var start = matcher.end();
        for (var index = start; index < metadata.length(); index++) {
            if (metadata.charAt(index) == quote && metadata.charAt(index - 1) != '\\') {
                return metadata.substring(start, index);
            }
        }
        return "";
    }

    private boolean templateUses(String template, String selector) {
        if (!StringUtils.hasText(template) || !StringUtils.hasText(selector)) {
            return false;
        }
        var normalized = selector.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            var attribute = normalized.substring(1, normalized.length() - 1);
            return Pattern.compile("(?s)<[^>]*\\s" + Pattern.quote(attribute) + "(?:\\s|=|>|\\])")
                    .matcher(template).find();
        }
        if (normalized.startsWith(".")) {
            var className = normalized.substring(1);
            return Pattern.compile("(?s)class\\s*=\\s*['\"][^'\"]*\\b" + Pattern.quote(className) + "\\b")
                    .matcher(template).find();
        }
        var tags = ELEMENT_TAG.matcher(template);
        while (tags.find()) {
            if (normalized.equalsIgnoreCase(tags.group(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean dynamicallyUses(String source, String componentSymbol) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(componentSymbol)) {
            return false;
        }
        return Pattern.compile(
                "(?s)(?:\\.open\\s*\\(|createComponent\\s*\\(|component\\s*:|portal\\s*:)\\s*"
                        + Pattern.quote(componentSymbol) + "\\b"
        ).matcher(source).find();
    }

    private int matching(String source, int start, char open, char close) {
        if (start < 0 || start >= source.length()) {
            return -1;
        }
        var depth = 0;
        var quote = '\0';
        var escaped = false;
        for (var index = start; index < source.length(); index++) {
            var current = source.charAt(index);
            if (quote != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private String relative(String sourcePath, String target) {
        if (!target.startsWith(".")) {
            return normalize(target);
        }
        var parent = sourcePath.contains("/") ? sourcePath.substring(0, sourcePath.lastIndexOf('/')) : "";
        return normalize(parent + "/" + target);
    }

    private String normalize(String path) {
        var result = new ArrayDeque<String>();
        for (var part : path.replace('\\', '/').split("/")) {
            if (!StringUtils.hasText(part) || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!result.isEmpty()) result.removeLast();
            } else {
                result.add(part);
            }
        }
        return String.join("/", result);
    }

    private void addEdge(
            LinkedHashMap<String, GitLabFrontendReachabilityEdge> edges,
            GitLabFrontendReachabilityEdge edge
    ) {
        edges.putIfAbsent(edge.fromId() + "|" + edge.toId() + "|" + edge.kind() + "|" + value(edge.memberSymbol()), edge);
    }

    private String stableId(String prefix, String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (var index = 0; index < 8; index++) hex.append("%02x".formatted(digest[index]));
            return prefix + "-" + hex;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String joinOrNone(List<String> values) {
        return values != null && !values.isEmpty() ? String.join(", ", values) : "none";
    }

    private String value(String value) {
        return value != null ? value : "";
    }

    private record ComponentDescriptor(
            String componentId,
            int sourceOrder,
            String symbol,
            List<String> selectors,
            String sourcePath,
            String templatePath,
            String template,
            String source
    ) {
        String selector() {
            return String.join(", ", selectors);
        }
    }

    private record ComponentDepth(String componentId, int depth, String discoveryKind) {
    }

    private record ComponentPosition(
            ComponentDescriptor descriptor,
            int depth,
            int order,
            boolean connected,
            String discoveryKind
    ) {
    }

    private static final class MutableComponent {
        private final ComponentPosition position;
        private final ComponentDescriptor descriptor;
        private final GitLabTypeScriptSymbolSliceResponse slice;
        private final LinkedHashSet<String> dependencyIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> childIds = new LinkedHashSet<>();

        private MutableComponent(ComponentPosition position, GitLabTypeScriptSymbolSliceResponse slice) {
            this.position = position;
            this.descriptor = position.descriptor();
            this.slice = slice;
        }

        private String id() {
            return descriptor.componentId();
        }

        private boolean partial() {
            return !"OK".equals(slice.status());
        }

        private GitLabFrontendReachabilityComponent toResponse() {
            return new GitLabFrontendReachabilityComponent(
                    id(), position.order(), position.depth(), position.connected(), position.discoveryKind(),
                    descriptor.symbol(), descriptor.selector(), descriptor.sourcePath(), descriptor.templatePath(),
                    slice.status(), slice.templateBindings(), slice.entrySymbols(), slice.includedSymbols(),
                    List.copyOf(dependencyIds), List.copyOf(childIds), slice.content(), slice.sourceCharacters(),
                    slice.returnedCharacters(), slice.truncated(), slice.limitations()
            );
        }
    }

    private static final class MutableDependency {
        private final String id;
        private final int order;
        private final GitLabFrontendReachabilityDependencyKind kind;
        private final String symbol;
        private final String sourcePath;
        private final String moduleSpecifier;
        private final LinkedHashSet<String> methods = new LinkedHashSet<>();
        private final LinkedHashSet<String> processedMethods = new LinkedHashSet<>();
        private final LinkedHashSet<String> usedBy = new LinkedHashSet<>();
        private final LinkedHashSet<String> downstreamDependencyIds = new LinkedHashSet<>();
        private String status;
        private String content = "";
        private int sourceCharacters;
        private int returnedCharacters;
        private boolean truncated;
        private List<String> limitations = List.of();

        private MutableDependency(
                String id,
                int order,
                GitLabFrontendReachabilityDependencyKind kind,
                String symbol,
                String sourcePath,
                String moduleSpecifier
        ) {
            this.id = id;
            this.order = order;
            this.kind = kind;
            this.symbol = symbol;
            this.sourcePath = sourcePath;
            this.moduleSpecifier = moduleSpecifier;
            this.status = StringUtils.hasText(sourcePath) ? "PENDING" : "EXTERNAL";
        }

        private void update(GitLabTypeScriptSymbolSliceResponse slice) {
            status = slice.status();
            content = slice.content();
            sourceCharacters = slice.sourceCharacters();
            returnedCharacters = slice.returnedCharacters();
            truncated = slice.truncated();
            limitations = slice.limitations();
        }

        private boolean partial() {
            return StringUtils.hasText(sourcePath) && !"OK".equals(status);
        }

        private GitLabFrontendReachabilityDependency toResponse() {
            return new GitLabFrontendReachabilityDependency(
                    id, order, kind, symbol, sourcePath, moduleSpecifier, status,
                    List.copyOf(methods), List.copyOf(usedBy), List.copyOf(downstreamDependencyIds),
                    content, sourceCharacters, returnedCharacters, truncated, limitations
            );
        }
    }

    private final class SourceIndex {
        private final Map<String, GitLabFrontendSourceFile> byPath = new LinkedHashMap<>();
        private final Map<String, List<String>> bySymbol = new LinkedHashMap<>();

        private SourceIndex(List<GitLabFrontendSourceFile> files) {
            for (var file : files) {
                byPath.put(file.path(), file);
                if (!file.path().endsWith(".ts") && !file.path().endsWith(".tsx")) continue;
                var symbols = DECLARED_SYMBOL.matcher(file.content());
                while (symbols.find()) {
                    bySymbol.computeIfAbsent(symbols.group(1), ignored -> new ArrayList<>()).add(file.path());
                }
            }
        }

        private String content(String path) {
            var file = byPath.get(path);
            return file != null ? file.content() : "";
        }

        private boolean declaresClass(String path, String symbol) {
            var file = byPath.get(path);
            return file != null && Pattern.compile("\\bclass\\s+" + Pattern.quote(value(symbol)) + "\\b")
                    .matcher(file.content()).find();
        }

        private String resolve(String ownerPath, String moduleSpecifier, String symbol) {
            if (StringUtils.hasText(moduleSpecifier)) {
                var direct = directCandidates(ownerPath, moduleSpecifier).stream()
                        .filter(byPath::containsKey).findFirst().orElse(null);
                if (direct != null) return direct;
                var suffix = moduleSpecifier.replace('\\', '/');
                var sourceMarker = suffix.indexOf("/src/");
                if (sourceMarker >= 0) suffix = suffix.substring(sourceMarker + 1);
                var expectedSuffix = suffix.endsWith(".ts") ? suffix : suffix + ".ts";
                var matched = byPath.keySet().stream().filter(path -> path.endsWith(expectedSuffix)).toList();
                if (matched.size() == 1) return matched.get(0);
            }
            var symbolPaths = bySymbol.getOrDefault(symbol, List.of());
            return symbolPaths.size() == 1 ? symbolPaths.get(0) : null;
        }

        private List<String> directCandidates(String ownerPath, String moduleSpecifier) {
            if (!moduleSpecifier.startsWith(".")) return List.of();
            var base = relative(ownerPath, moduleSpecifier);
            return List.of(base, base + ".ts", base + ".tsx", base + "/index.ts");
        }
    }
}
