package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

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
    private static final Pattern IMPORT_STATEMENT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?"
    );
    private static final Pattern COMPONENT_IMPORTS = Pattern.compile("\\bimports\\s*:\\s*\\[");
    private static final Pattern DYNAMIC_COMPONENT_SYMBOL = Pattern.compile(
            "(?s)(?:\\.open\\s*\\(|createComponent\\s*\\(|component\\s*:|portal\\s*:)\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Set<String> DATA_MODEL_SYMBOLS = Set.of(
            "string", "number", "boolean", "bigint", "symbol", "object", "unknown", "any", "void",
            "Array", "ReadonlyArray", "Map", "Set", "Promise", "Record", "Date"
    );
    private static final Set<String> ANGULAR_FRAMEWORK_SYMBOLS = Set.of(
            "Component", "Directive", "Pipe", "Injectable", "Inject", "inject", "Input", "Output",
            "ViewChild", "ViewChildren", "ContentChild", "ContentChildren", "EventEmitter", "TemplateRef",
            "ElementRef", "DestroyRef", "ChangeDetectorRef", "signal", "computed", "effect", "input", "output",
            "toSignal", "takeUntilDestroyed"
    );

    private final GitLabFrontendScreenGraphContextService screenGraphContextService;
    private final GitLabTypeScriptSymbolSliceService typeScriptSymbolSliceService;
    private final GitLabRepositoryPort repositoryPort;

    public GitLabFrontendScreenReachabilityGraph build(GitLabFrontendScreenGraphContextRequest request) {
        var context = screenGraphContextService.buildReachabilitySeed(request);
        var bootstrapPath = context.effectiveRouteChain().segments().isEmpty()
                ? context.screenNode().viewTarget().sourcePath()
                : context.effectiveRouteChain().segments().get(0).source().path();
        var sourceIndex = new SourceIndex(
                context.sourceFiles(), repositoryPort, request.scope(), request.limits(), bootstrapPath
        );
        var descriptors = new ArrayList<>(componentDescriptors(context, sourceIndex));
        var root = rootDescriptor(context, descriptors);
        var edgeIndex = new LinkedHashMap<String, GitLabFrontendReachabilityEdge>();
        var components = new LinkedHashMap<String, MutableComponent>();
        var dependencies = new LinkedHashMap<String, MutableDependency>();
        var dependencyQueue = new ArrayDeque<String>();
        var queuedDependencies = new LinkedHashSet<String>();
        var componentQueue = new ArrayDeque<ComponentDepth>();
        var queuedComponents = new LinkedHashSet<String>();
        if (root != null) {
            componentQueue.add(new ComponentDepth(root.componentId(), 0, "SELECTED_SCREEN"));
            queuedComponents.add(root.componentId());
        }
        while (!componentQueue.isEmpty()) {
            var current = componentQueue.removeFirst();
            var descriptor = descriptorById(descriptors, current.componentId());
            if (descriptor == null || components.containsKey(descriptor.componentId())) {
                continue;
            }
            discoverComponentCandidates(descriptor, sourceIndex, descriptors);
            var position = new ComponentPosition(
                    descriptor, current.depth(), components.size(), true, current.discoveryKind()
            );
            var slice = componentSlice(request.scope(), descriptor);
            var component = new MutableComponent(position, slice);
            components.put(descriptor.componentId(), component);
            for (var edge : componentRelations(descriptor, descriptors)) {
                addEdge(edgeIndex, edge);
                component.childIds.add(edge.toId());
                if (queuedComponents.add(edge.toId())) {
                    componentQueue.add(new ComponentDepth(edge.toId(), current.depth() + 1, edge.kind().name()));
                }
            }
            collectDependencies(
                    request.scope(), component.id(), component.descriptor.sourcePath(), component.slice.downstreamReferences(),
                    sourceIndex, descriptors, dependencies, dependencyQueue, queuedDependencies, edgeIndex,
                    component.dependencyIds, componentQueue, queuedComponents, current.depth() + 1
            );
        }

        processDependencies(
                request.scope(), sourceIndex, descriptors, dependencies,
                dependencyQueue, queuedDependencies, edgeIndex
        );

        var connectedComponents = components.values().stream().map(MutableComponent::toResponse).toList();
        var levels = connectedComponents.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GitLabFrontendReachabilityComponent::depth,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> new GitLabFrontendReachabilityComponentLevel(entry.getKey(), entry.getValue()))
                .toList();
        var dependencyResponses = dependencies.values().stream()
                .sorted(Comparator.comparingInt(dependency -> dependency.order))
                .map(MutableDependency::toResponse)
                .toList();
        var edges = List.copyOf(edgeIndex.values());
        var limitations = limitations(context, root, components, dependencies, sourceIndex);
        var partial = root == null || context.graphCoverage().limitReached() || sourceIndex.limitReached()
                || components.values().stream().anyMatch(MutableComponent::partial)
                || dependencies.values().stream().anyMatch(MutableDependency::partial);
        var status = root == null ? "BLOCKED" : partial ? "PARTIAL" : "OK";
        var outline = readableOutline(
                status, context, levels, dependencyResponses, edges, limitations
        );
        var sliceCharacters = components.values().stream().mapToInt(component -> component.slice.returnedCharacters()).sum()
                + dependencies.values().stream().mapToInt(dependency -> dependency.returnedCharacters).sum();
        return new GitLabFrontendScreenReachabilityGraph(
                context.scope(), context.sourceRevision(), status, context.screenNode(), context.effectiveRouteChain(),
                levels, dependencyResponses, edges, context.technicalSignals(),
                reachabilityDiagnostics(context), sourceIndex.sourceFileCount(), sourceIndex.sourceCharacters(),
                sliceCharacters, outline.length(), sourceIndex.limitReached() || context.graphCoverage().limitReached(),
                limitations, outline
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
            var parsed = componentDescriptors(file.path(), file.content(), sourceIndex, sourceOrder);
            result.addAll(parsed);
            sourceOrder += parsed.size();
        }
        return List.copyOf(result);
    }

    private List<ComponentDescriptor> componentDescriptors(
            String sourcePath,
            String source,
            SourceIndex sourceIndex,
            int sourceOrder
    ) {
        var result = new ArrayList<ComponentDescriptor>();
        var matcher = COMPONENT_DECORATOR.matcher(source);
        while (matcher.find()) {
            var open = source.indexOf('(', matcher.start());
            var close = matching(source, open, '(', ')');
            if (close < 0) {
                continue;
            }
            var classMatcher = COMPONENT_CLASS.matcher(source);
            classMatcher.region(close + 1, source.length());
            if (!classMatcher.find()) {
                continue;
            }
            var metadata = source.substring(open + 1, close);
            var symbol = classMatcher.group(1);
            var selectors = selectorValues(metadata);
            var templatePath = templatePath(sourcePath, metadata);
            var template = StringUtils.hasText(templatePath)
                    ? sourceIndex.content(templatePath)
                    : inlineTemplate(metadata);
            result.add(new ComponentDescriptor(
                    stableId("component", sourcePath + "#" + symbol), sourceOrder + result.size(), symbol,
                    selectors, sourcePath, templatePath, template, source
            ));
        }
        return List.copyOf(result);
    }

    private void discoverComponentCandidates(
            ComponentDescriptor owner,
            SourceIndex sourceIndex,
            List<ComponentDescriptor> descriptors
    ) {
        var symbols = new LinkedHashSet<String>();
        var imported = sourceIndex.importBindings(owner.sourcePath());
        imported.keySet().stream()
                .filter(symbol -> symbol.matches(".*(?:Component|Dialog|View|Page|Form|Panel|Tab|Widget)$"))
                .forEach(symbols::add);
        symbols.addAll(standaloneComponentImports(owner.source()));
        var dynamic = DYNAMIC_COMPONENT_SYMBOL.matcher(owner.source());
        while (dynamic.find()) {
            symbols.add(dynamic.group(1));
        }
        for (var symbol : symbols) {
            var module = imported.get(symbol);
            var path = sourceIndex.resolve(owner.sourcePath(), module, symbol);
            addDescriptorsFromPath(path, sourceIndex, descriptors);
        }
        var tags = ELEMENT_TAG.matcher(owner.template());
        while (tags.find()) {
            var selector = tags.group(1).toLowerCase(Locale.ROOT);
            if (!selector.contains("-") || descriptors.stream()
                    .anyMatch(descriptor -> descriptor.selectors().stream()
                            .anyMatch(candidate -> selectorMatchesElement(candidate, selector)))) {
                continue;
            }
            for (var path : sourceIndex.findSelectorCandidates(selector)) {
                addDescriptorsFromPath(path, sourceIndex, descriptors);
            }
        }
    }

    private void addDescriptorsFromPath(
            String path,
            SourceIndex sourceIndex,
            List<ComponentDescriptor> descriptors
    ) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        var source = sourceIndex.content(path);
        if (!StringUtils.hasText(source)) {
            return;
        }
        var existing = descriptors.stream()
                .filter(descriptor -> descriptor.sourcePath().equals(path))
                .map(ComponentDescriptor::symbol)
                .collect(java.util.stream.Collectors.toSet());
        componentDescriptors(path, source, sourceIndex, descriptors.size()).stream()
                .filter(descriptor -> !existing.contains(descriptor.symbol()))
                .forEach(descriptors::add);
    }

    private Set<String> standaloneComponentImports(String source) {
        var result = new LinkedHashSet<String>();
        var matcher = COMPONENT_IMPORTS.matcher(source);
        while (matcher.find()) {
            var open = source.indexOf('[', matcher.start());
            var close = matching(source, open, '[', ']');
            if (close < 0) {
                continue;
            }
            var identifiers = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*")
                    .matcher(source.substring(open + 1, close));
            while (identifiers.find()) {
                result.add(identifiers.group());
            }
        }
        return result;
    }

    private ComponentDescriptor descriptorById(List<ComponentDescriptor> descriptors, String componentId) {
        return descriptors.stream()
                .filter(descriptor -> descriptor.componentId().equals(componentId))
                .findFirst().orElse(null);
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

    private List<GitLabFrontendReachabilityEdge> componentRelations(
            ComponentDescriptor parent,
            List<ComponentDescriptor> descriptors
    ) {
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
        return List.copyOf(edges);
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
            LinkedHashSet<String> ownerDependencyIds,
            ArrayDeque<ComponentDepth> componentQueue,
            Set<String> queuedComponents,
            int componentDepth
    ) {
        for (var reference : references) {
            if (!materialDependency(reference)) {
                continue;
            }
            var symbol = StringUtils.hasText(reference.targetSymbol())
                    ? reference.targetSymbol() : reference.ownerSymbol();
            var boundModule = sourceIndex.moduleForSymbol(ownerPath, symbol);
            var moduleSpecifier = StringUtils.hasText(boundModule) ? boundModule : reference.moduleSpecifier();
            var targetPath = sourceIndex.resolve(ownerPath, moduleSpecifier, symbol);
            addDescriptorsFromPath(targetPath, sourceIndex, descriptors);
            var componentTarget = descriptors.stream()
                    .filter(descriptor -> descriptor.sourcePath().equals(targetPath))
                    .filter(descriptor -> descriptor.symbol().equals(symbol))
                    .findFirst().orElse(null);
            if (componentTarget != null) {
                addEdge(edges, new GitLabFrontendReachabilityEdge(
                        ownerId, componentTarget.componentId(), GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE,
                        reference.kind().name(), ownerPath, reference.sourceSymbol(), reference.memberSymbol()
                ));
                if (componentQueue != null && queuedComponents != null
                        && queuedComponents.add(componentTarget.componentId())) {
                    componentQueue.add(new ComponentDepth(
                            componentTarget.componentId(), componentDepth,
                            GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE.name()
                    ));
                }
                continue;
            }
            var dependencyId = stableId(
                    "dependency",
                    (StringUtils.hasText(targetPath) ? targetPath : value(moduleSpecifier)) + "#" + value(symbol)
            );
            var kind = dependencyKind(reference, symbol);
            var category = dependencyCategory(kind, symbol, moduleSpecifier, targetPath);
            var dependency = dependencies.computeIfAbsent(dependencyId, ignored -> new MutableDependency(
                    dependencyId, dependencies.size(), kind, category, symbol,
                    targetPath, moduleSpecifier
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
            if (StringUtils.hasText(targetPath) && sliceDependency(category) && queued.add(dependencyId)) {
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
                    dependencies, queue, queued, edges, downstream, null, null, 0
            );
            dependency.downstreamDependencyIds.addAll(downstream);
        }
    }

    private List<String> limitations(
            GitLabFrontendScreenGraphContext context,
            ComponentDescriptor root,
            Map<String, MutableComponent> components,
            Map<String, MutableDependency> dependencies,
            SourceIndex sourceIndex
    ) {
        var result = new LinkedHashSet<String>();
        result.addAll(context.graphCoverage().limitations());
        if (root == null) {
            result.add("Selected screen component was not found among deterministically delivered component sources.");
        }
        if (sourceIndex.limitReached()) {
            result.add("On-demand reachability traversal could not close every confirmed source edge.");
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

    private List<GitLabFrontendGraphDiagnostic> reachabilityDiagnostics(
            GitLabFrontendScreenGraphContext context
    ) {
        return context.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code()
                        != GitLabFrontendGraphDiagnosticCode.CONTEXT_FILE_LIMIT_REACHED)
                .toList();
    }

    private String readableOutline(
            String status,
            GitLabFrontendScreenGraphContext context,
            List<GitLabFrontendReachabilityComponentLevel> levels,
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
        var dependencyReferences = new LinkedHashMap<String, String>();
        dependencies.stream().filter(this::relevantDependency).forEach(dependency -> dependencyReferences.put(
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
            result.append(routeOrder++).append(". route `").append(segment.routePattern()).append("`")
                    .append(" — path segment `")
                    .append(StringUtils.hasText(segment.pathSegment()) ? segment.pathSegment() : "(empty container)")
                    .append("` — outlet `").append(value(segment.outlet())).append("`")
                    .append(" — source `").append(segment.source().path()).append("`");
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
        var relevantDependencies = dependencies.stream().filter(this::relevantDependency).toList();
        result.append("\n## Functional and supporting dependencies\n");
        for (var dependency : relevantDependencies) {
            result.append("\n### [D").append(dependency.discoveryOrder() + 1).append("] ")
                    .append(value(dependency.symbol())).append("\n");
            result.append("- kind: `").append(dependency.kind()).append("`\n");
            result.append("- category: `").append(dependency.category()).append("`\n");
            result.append("- source: `").append(StringUtils.hasText(dependency.sourcePath())
                    ? dependency.sourcePath() : value(dependency.moduleSpecifier())).append("`\n");
            result.append("- methods: ").append(joinOrNone(dependency.methods())).append("\n");
            result.append("- used by: ").append(joinOrNone(dependency.usedBy().stream()
                    .map(id -> componentReferences.getOrDefault(
                            id, dependencyReferences.getOrDefault(id, id)
                    )).toList())).append("\n");
            var downstream = dependency.downstreamDependencyIds().stream()
                    .filter(dependencyReferences::containsKey)
                    .map(dependencyReferences::get)
                    .toList();
            if (!downstream.isEmpty()) {
                result.append("- downstream: ").append(String.join(", ", downstream)).append("\n");
            }
        }
        var hiddenByCategory = dependencies.stream()
                .filter(dependency -> !relevantDependencies.contains(dependency))
                .collect(java.util.stream.Collectors.groupingBy(
                        GitLabFrontendReachabilityDependency::category,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        if (!hiddenByCategory.isEmpty()) {
            result.append("\n## Technical dependency summary\n");
            hiddenByCategory.forEach((category, count) -> result.append("- ")
                    .append(category).append(": ").append(count).append(" canonical reference(s)\n"));
        }
        if (!limitations.isEmpty()) {
            result.append("\n## Explicit boundaries\n");
            limitations.forEach(limitation -> result.append("- ").append(limitation).append("\n"));
        }
        return result.toString().stripTrailing();
    }

    private boolean relevantDependency(GitLabFrontendReachabilityDependency dependency) {
        return dependency.category() == GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL
                || dependency.category() == GitLabFrontendReachabilityDependencyCategory.SUPPORTING_CODE;
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
        var entrySymbols = component.entrySymbols().stream()
                .map(GitLabTypeScriptSymbolCandidate::symbolName).distinct().toList();
        result.append("- UI entry points: ").append(compactNames(entrySymbols, 12)).append("\n");
        var relevantDependencyIds = component.dependencyIds().stream()
                .filter(id -> dependencyReferences.containsKey(id))
                .toList();
        result.append("- relevant dependencies: ").append(joinOrNone(relevantDependencyIds.stream()
                .map(id -> dependencyReferences.getOrDefault(id, id)).toList())).append("\n");
        var children = edges.stream()
                .filter(edge -> edge.fromId().equals(component.componentId()))
                .filter(edge -> edge.kind() == GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD
                        || edge.kind() == GitLabFrontendReachabilityEdgeKind.DYNAMIC_COMPONENT
                        || edge.kind() == GitLabFrontendReachabilityEdgeKind.COMPONENT_REFERENCE)
                .map(edge -> componentReferences.getOrDefault(edge.toId(), edge.toId())
                        + " (" + edge.kind() + ")")
                .toList();
        result.append("- children: ").append(joinOrNone(children)).append("\n");
    }

    private String compactNames(List<String> names, int maximum) {
        if (names == null || names.isEmpty()) {
            return "none (static presentation only)";
        }
        var shown = names.stream().limit(maximum).toList();
        return String.join(", ", shown) + (names.size() > maximum
                ? " ... " + (names.size() - maximum) + " additional entry point(s)"
                : "");
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

    private GitLabFrontendReachabilityDependencyCategory dependencyCategory(
            GitLabFrontendReachabilityDependencyKind kind,
            String symbol,
            String moduleSpecifier,
            String sourcePath
    ) {
        var module = value(moduleSpecifier).toLowerCase(Locale.ROOT);
        if (DATA_MODEL_SYMBOLS.contains(value(symbol))) {
            return GitLabFrontendReachabilityDependencyCategory.DATA_MODEL;
        }
        if (value(symbol).matches(".*(?:Dto|DTO|Model|Response|Request|Payload|Entity|Data)$")) {
            return GitLabFrontendReachabilityDependencyCategory.DATA_MODEL;
        }
        if (module.startsWith("@angular/") || ANGULAR_FRAMEWORK_SYMBOLS.contains(value(symbol))) {
            return GitLabFrontendReachabilityDependencyCategory.FRAMEWORK;
        }
        if (kind == GitLabFrontendReachabilityDependencyKind.RXJS
                || module.equals("rxjs") || module.startsWith("rxjs/")) {
            return GitLabFrontendReachabilityDependencyCategory.REACTIVE;
        }
        if (kind == GitLabFrontendReachabilityDependencyKind.FACADE
                || kind == GitLabFrontendReachabilityDependencyKind.SERVICE
                || kind == GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT
                || kind == GitLabFrontendReachabilityDependencyKind.NGRX
                || kind == GitLabFrontendReachabilityDependencyKind.WEBSOCKET) {
            return GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL;
        }
        if (StringUtils.hasText(sourcePath)) {
            return GitLabFrontendReachabilityDependencyCategory.SUPPORTING_CODE;
        }
        return GitLabFrontendReachabilityDependencyCategory.FRAMEWORK;
    }

    private boolean sliceDependency(GitLabFrontendReachabilityDependencyCategory category) {
        return category == GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL
                || category == GitLabFrontendReachabilityDependencyCategory.SUPPORTING_CODE;
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
        if (!matcher.find()) {
            return null;
        }
        var target = matcher.group(2).trim();
        var parent = sourcePath.contains("/") ? sourcePath.substring(0, sourcePath.lastIndexOf('/')) : "";
        return normalize(parent + "/" + target);
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
        var normalized = selector.trim().replaceAll(":not\\([^)]*\\)", "");
        var tagMatcher = Pattern.compile("^([A-Za-z][A-Za-z0-9_.:-]*)").matcher(normalized);
        var requiredTag = tagMatcher.find() ? tagMatcher.group(1) : null;
        var requiredAttributes = new ArrayList<String>();
        var attributes = Pattern.compile("\\[([A-Za-z_$][A-Za-z0-9_$.-]*)[^]]*]").matcher(normalized);
        while (attributes.find()) {
            requiredAttributes.add(attributes.group(1));
        }
        var requiredClasses = new ArrayList<String>();
        var classes = Pattern.compile("\\.([A-Za-z_$][A-Za-z0-9_$-]*)").matcher(normalized);
        while (classes.find()) {
            requiredClasses.add(classes.group(1));
        }
        var elements = Pattern.compile("(?s)<\\s*([A-Za-z][A-Za-z0-9_.:-]*)([^>]*)>").matcher(template);
        while (elements.find()) {
            if (StringUtils.hasText(requiredTag) && !requiredTag.equalsIgnoreCase(elements.group(1))) {
                continue;
            }
            var elementAttributes = elements.group(2);
            if (requiredAttributes.stream().anyMatch(attribute -> !hasTemplateAttribute(elementAttributes, attribute))) {
                continue;
            }
            if (requiredClasses.stream().anyMatch(className -> !hasTemplateClass(elementAttributes, className))) {
                continue;
            }
            return StringUtils.hasText(requiredTag) || !requiredAttributes.isEmpty() || !requiredClasses.isEmpty();
        }
        return false;
    }

    private boolean selectorMatchesElement(String selector, String elementName) {
        var matcher = Pattern.compile("^([A-Za-z][A-Za-z0-9_.:-]*)").matcher(selector.trim());
        return matcher.find() && matcher.group(1).equalsIgnoreCase(elementName);
    }

    private boolean hasTemplateAttribute(String attributes, String attribute) {
        return Pattern.compile(
                "(?s)(?:^|\\s)(?:\\[\\(?|\\()?" + Pattern.quote(attribute)
                        + "(?:\\)?]|\\))?(?=\\s|=|$)"
        ).matcher(attributes).find();
    }

    private boolean hasTemplateClass(String attributes, String className) {
        return Pattern.compile(
                "(?s)\\bclass\\s*=\\s*['\"][^'\"]*(?:^|\\s)" + Pattern.quote(className)
                        + "(?:\\s|$)[^'\"]*['\"]"
        ).matcher(attributes).find();
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
            return !"OK".equals(slice.status()) && !"STATIC_PRESENTATIONAL".equals(slice.status());
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
        private final GitLabFrontendReachabilityDependencyCategory category;
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
                GitLabFrontendReachabilityDependencyCategory category,
                String symbol,
                String sourcePath,
                String moduleSpecifier
        ) {
            this.id = id;
            this.order = order;
            this.kind = kind;
            this.category = category;
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
            return StringUtils.hasText(sourcePath) && !methods.isEmpty()
                    && !"OK".equals(status) && !"STATIC_PRESENTATIONAL".equals(status);
        }

        private GitLabFrontendReachabilityDependency toResponse() {
            return new GitLabFrontendReachabilityDependency(
                    id, order, kind, category, symbol, sourcePath, moduleSpecifier,
                    "PENDING".equals(status) && methods.isEmpty() ? "REFERENCE_ONLY" : status,
                    List.copyOf(methods), List.copyOf(usedBy), List.copyOf(downstreamDependencyIds),
                    content, sourceCharacters, returnedCharacters, truncated, limitations
            );
        }
    }

    private final class SourceIndex {
        private static final Pattern RE_EXPORT = Pattern.compile(
                "(?ms)^\\s*export\\s+(?:\\{([^}]*)}|\\*)\\s+from\\s+['\"]([^'\"]+)['\"]"
        );

        private final Map<String, GitLabFrontendSourceFile> byPath = new LinkedHashMap<>();
        private final Map<String, List<String>> bySymbol = new LinkedHashMap<>();
        private final GitLabRepositoryPort repositoryPort;
        private final GitLabFrontendRepositoryScope scope;
        private final GitLabFrontendTargetedSourceSession session;
        private final GitLabFrontendTargetedImportResolver importResolver;
        private final Set<String> selectorSearches = new LinkedHashSet<>();
        private final Map<String, String> symbolSearches = new LinkedHashMap<>();

        private SourceIndex(
                List<GitLabFrontendSourceFile> files,
                GitLabRepositoryPort repositoryPort,
                GitLabFrontendRepositoryScope scope,
                GitLabFrontendGraphLimits limits,
                String bootstrapPath
        ) {
            this.repositoryPort = repositoryPort;
            this.scope = scope;
            this.session = new GitLabFrontendTargetedSourceSession(repositoryPort, scope, limits, false);
            this.importResolver = new GitLabFrontendTargetedImportResolver(session, bootstrapPath);
            files.forEach(this::add);
        }

        private void add(GitLabFrontendSourceFile file) {
            var path = normalize(file.path());
            if (byPath.putIfAbsent(path, file) != null
                    || !path.endsWith(".ts") && !path.endsWith(".tsx")) {
                return;
            }
            var symbols = DECLARED_SYMBOL.matcher(file.content());
            while (symbols.find()) {
                bySymbol.computeIfAbsent(symbols.group(1), ignored -> new ArrayList<>()).add(path);
            }
        }

        private String content(String rawPath) {
            var path = normalize(rawPath);
            var current = byPath.get(path);
            if (current != null) {
                return current.content();
            }
            var source = session.readOptional(path);
            if (source == null) {
                return "";
            }
            add(new GitLabFrontendSourceFile(
                    path, List.of(GitLabFrontendSourceRole.RELATED_SOURCE), source, source.length(), false
            ));
            return source;
        }

        private boolean declaresClass(String path, String symbol) {
            var source = content(path);
            return StringUtils.hasText(source) && Pattern.compile(
                    "\\bclass\\s+" + Pattern.quote(value(symbol)) + "\\b"
            ).matcher(source).find();
        }

        private String resolve(String ownerPath, String moduleSpecifier, String symbol) {
            if (StringUtils.hasText(moduleSpecifier)) {
                var direct = directCandidates(ownerPath, moduleSpecifier).stream()
                        .filter(byPath::containsKey).findFirst().orElse(null);
                if (direct != null) {
                    return resolveReExport(direct, symbol, new LinkedHashSet<>());
                }
                var suffixMatch = loadedSuffixMatch(moduleSpecifier);
                if (StringUtils.hasText(suffixMatch)) {
                    return resolveReExport(suffixMatch, symbol, new LinkedHashSet<>());
                }
                for (var resolved : importResolver.resolve(ownerPath, moduleSpecifier)) {
                    content(resolved);
                    return resolveReExport(resolved, symbol, new LinkedHashSet<>());
                }
                var searched = findSymbolCandidate(symbol);
                if (StringUtils.hasText(searched)) {
                    return searched;
                }
                // An explicit import binding is authoritative. Never map an external
                // Angular or organizational symbol to an unrelated local declaration.
                return null;
            }
            var symbolPaths = bySymbol.getOrDefault(symbol, List.of());
            return symbolPaths.size() == 1 ? symbolPaths.get(0) : null;
        }

        private String findSymbolCandidate(String symbol) {
            if (!StringUtils.hasText(symbol)
                    || !symbol.matches(".*(?:Component|Dialog|Facade|Service|Client|Api|API|Controller|Store)$")) {
                return null;
            }
            if (symbolSearches.containsKey(symbol)) {
                return symbolSearches.get(symbol);
            }
            var candidates = new LinkedHashSet<String>();
            try {
                for (var declaration : List.of("class", "const", "function")) {
                    repositoryPort.searchRepositoryFilesByContent(
                                    scope.group(), scope.projectName(), scope.ref(),
                                    List.of("export " + declaration + " " + symbol), 10
                            ).stream()
                            .map(candidate -> normalize(candidate.filePath()))
                            .filter(path -> path.endsWith(".ts") || path.endsWith(".tsx"))
                            .forEach(candidates::add);
                }
            } catch (RuntimeException ignored) {
                symbolSearches.put(symbol, "");
                return null;
            }
            var matching = candidates.stream()
                    .filter(path -> declaresSymbol(content(path), symbol))
                    .toList();
            var result = matching.size() == 1 ? matching.get(0) : "";
            symbolSearches.put(symbol, result);
            return StringUtils.hasText(result) ? result : null;
        }

        private String resolveReExport(String path, String symbol, Set<String> visited) {
            var normalized = normalize(path);
            if (!visited.add(normalized)) {
                return normalized;
            }
            var source = content(normalized);
            if (!StringUtils.hasText(symbol) || declaresSymbol(source, symbol)) {
                return normalized;
            }
            var matcher = RE_EXPORT.matcher(source);
            while (matcher.find()) {
                if (matcher.group(1) != null && !exportContains(matcher.group(1), symbol)) {
                    continue;
                }
                for (var target : importResolver.resolve(normalized, matcher.group(2))) {
                    content(target);
                    var resolved = resolveReExport(target, symbol, visited);
                    if (declaresSymbol(content(resolved), symbol)) {
                        return resolved;
                    }
                }
            }
            return normalized;
        }

        private boolean exportContains(String bindings, String symbol) {
            for (var binding : bindings.split(",")) {
                var parts = binding.trim().replaceFirst("^type\\s+", "").split("\\s+as\\s+");
                var exported = parts.length > 1 ? parts[1].trim() : parts[0].trim();
                if (symbol.equals(exported)) {
                    return true;
                }
            }
            return false;
        }

        private boolean declaresSymbol(String source, String symbol) {
            return StringUtils.hasText(source) && Pattern.compile(
                    "\\b(?:class|interface|enum|function|const)\\s+" + Pattern.quote(value(symbol)) + "\\b"
            ).matcher(source).find();
        }

        private String loadedSuffixMatch(String moduleSpecifier) {
            var suffix = moduleSpecifier.replace('\\', '/');
            var sourceMarker = suffix.indexOf("/src/");
            if (sourceMarker >= 0) {
                suffix = suffix.substring(sourceMarker + 1);
            }
            var candidates = new LinkedHashSet<String>();
            candidates.add(suffix);
            candidates.add(suffix + ".ts");
            candidates.add(suffix + ".tsx");
            candidates.add(suffix + "/index.ts");
            var matched = byPath.keySet().stream()
                    .filter(path -> candidates.stream().anyMatch(path::endsWith))
                    .toList();
            return matched.size() == 1 ? matched.get(0) : null;
        }

        private String moduleForSymbol(String ownerPath, String symbol) {
            return importBindings(ownerPath).get(symbol);
        }

        private Map<String, String> importBindings(String ownerPath) {
            var source = content(ownerPath);
            var result = new LinkedHashMap<String, String>();
            var matcher = IMPORT_STATEMENT.matcher(source);
            while (matcher.find()) {
                var bindings = value(matcher.group(1)).trim();
                var module = matcher.group(2);
                if (!StringUtils.hasText(bindings)) {
                    continue;
                }
                var braceStart = bindings.indexOf('{');
                var braceEnd = bindings.lastIndexOf('}');
                if (braceStart >= 0 && braceEnd > braceStart) {
                    var leading = bindings.substring(0, braceStart).replace(",", "").trim();
                    if (StringUtils.hasText(leading)) {
                        result.put(leading.replaceFirst("^type\\s+", "").trim(), module);
                    }
                    for (var binding : bindings.substring(braceStart + 1, braceEnd).split(",")) {
                        var parts = binding.trim().replaceFirst("^type\\s+", "").split("\\s+as\\s+");
                        if (StringUtils.hasText(parts[0])) {
                            result.put(parts.length > 1 ? parts[1].trim() : parts[0].trim(), module);
                        }
                    }
                } else {
                    var namespace = Pattern.compile("\\*\\s+as\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
                            .matcher(bindings);
                    if (namespace.find()) {
                        result.put(namespace.group(1), module);
                    } else {
                        var defaultBinding = bindings.split(",")[0].replaceFirst("^type\\s+", "").trim();
                        if (StringUtils.hasText(defaultBinding)) {
                            result.put(defaultBinding, module);
                        }
                    }
                }
            }
            return result;
        }

        private List<String> findSelectorCandidates(String selector) {
            if (!selectorSearches.add(selector)) {
                return List.of();
            }
            var result = new LinkedHashSet<String>();
            try {
                for (var quote : List.of("'", "\"")) {
                    repositoryPort.searchRepositoryFilesByContent(
                                    scope.group(), scope.projectName(), scope.ref(),
                                    List.of("selector: " + quote + selector), 10
                            ).stream()
                            .map(candidate -> normalize(candidate.filePath()))
                            .filter(path -> path.endsWith(".ts") || path.endsWith(".tsx"))
                            .forEach(result::add);
                }
            } catch (RuntimeException ignored) {
                return List.of();
            }
            result.forEach(this::content);
            return List.copyOf(result);
        }

        private List<String> directCandidates(String ownerPath, String moduleSpecifier) {
            if (!moduleSpecifier.startsWith(".")) return List.of();
            var base = relative(ownerPath, moduleSpecifier);
            return List.of(base, base + ".ts", base + ".tsx", base + "/index.ts");
        }

        private int sourceFileCount() {
            return byPath.size();
        }

        private int sourceCharacters() {
            return byPath.values().stream().mapToInt(GitLabFrontendSourceFile::returnedCharacters).sum();
        }

        private boolean limitReached() {
            return session.limitReached();
        }
    }
}
