package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class GitLabFrontendRouteSourceTraversalService {

    private static final Pattern NAMED_DEFAULT_DECLARATION = Pattern.compile(
            "(?s)\\bexport\\s+default\\s+(?:abstract\\s+)?(?:class|function)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern DEFAULT_IDENTIFIER_EXPORT = Pattern.compile(
            "(?s)\\bexport\\s+default\\s+(?!class\\b|function\\b)([A-Za-z_$][A-Za-z0-9_$]*)\\s*;"
    );
    private static final Pattern LOCAL_DEFAULT_EXPORT = Pattern.compile(
            "(?s)\\bexport\\s*\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s+as\\s+default\\s*}"
    );

    private final GitLabRepositoryPort repositoryPort;
    private final AngularBootstrapSourceParser moduleParser = new AngularBootstrapSourceParser();
    private final AngularRouteSourceParser routeParser = new AngularRouteSourceParser();

    GitLabFrontendRouteSourceTraversalResult traverse(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendBootstrapRoot root,
            GitLabFrontendGraphLimits limits
    ) {
        var effectiveLimits = limits != null ? limits : GitLabFrontendGraphLimits.defaults();
        var session = new GitLabFrontendTargetedSourceSession(repositoryPort, scope, effectiveLimits);
        var imports = new GitLabFrontendTargetedImportResolver(session, root.bootstrapSource().path());
        var staticStrings = new TypeScriptStaticRouteResolver(session::readOptional, imports::resolve);
        var collections = new ArrayList<GitLabFrontendRouteSourceTraversalResult.RouteCollection>();
        var components = new LinkedHashMap<String, GitLabFrontendRouteSourceTraversalResult.ComponentTarget>();
        var pendingComponents = new ArrayList<PendingComponent>();
        var queue = new ArrayDeque<RouteTask>();
        var unresolvedEdges = new Counter();
        var routeNodeCount = new Counter();

        enqueueRoot(root, session, imports, queue, unresolvedEdges);
        var processed = new LinkedHashSet<String>();
        while (!queue.isEmpty()) {
            var task = queue.removeFirst();
            var taskKey = task.collectionId();
            if (!processed.add(taskKey)) {
                continue;
            }
            if (task.ancestry().contains(task.sourcePath() + "#" + value(task.symbol()))) {
                unresolvedEdges.increment();
                session.diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.IMPORT_CYCLE_DETECTED,
                        "A route import or re-export cycle was detected.",
                        task.sourcePath()
                );
                continue;
            }
            if (!session.withinImportDepth(task.depth(), task.sourcePath())
                    || !session.markRouteFile(task.sourcePath())) {
                unresolvedEdges.increment();
                continue;
            }
            var source = session.readRequired(task.sourcePath());
            if (source == null) {
                unresolvedEdges.increment();
                continue;
            }
            var parsed = routeParser.parseCollection(
                    task.sourcePath(),
                    source,
                    task.symbol(),
                    task.parentRoutePath(),
                    task.inheritedLazy(),
                    task.inheritedGuards(),
                    staticStrings
            );
            if (!parsed.limitations().isEmpty()) {
                unresolvedEdges.increment();
                for (var limitation : parsed.limitations()) {
                    session.diagnostic(
                            GitLabFrontendDiagnosticSeverity.WARNING,
                            GitLabFrontendGraphDiagnosticCode.DYNAMIC_ROUTE_EXPRESSION,
                            limitation,
                            task.sourcePath()
                    );
                }
            }
            if (parsed.routes().isEmpty()) {
                unresolvedEdges.increment();
                session.diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.ROUTE_COLLECTION_NOT_FOUND,
                        "A targeted route collection could not be parsed as a static route array.",
                        task.sourcePath()
                );
            }
            collections.add(new GitLabFrontendRouteSourceTraversalResult.RouteCollection(
                    task.collectionId(),
                    task.parentRoute(),
                    task.sourcePath(),
                    task.symbol(),
                    task.parentRoutePath(),
                    task.relation(),
                    parsed
            ));
            for (var route : parsed.routes()) {
                if (routeNodeCount.value >= effectiveLimits.maxRouteNodes()) {
                    session.diagnostic(
                            GitLabFrontendDiagnosticSeverity.WARNING,
                            GitLabFrontendGraphDiagnosticCode.ROUTE_NODE_LIMIT_REACHED,
                            "Targeted traversal reached maxRouteNodes=" + effectiveLimits.maxRouteNodes() + ".",
                            task.sourcePath()
                    );
                    unresolvedEdges.increment();
                    queue.clear();
                    break;
                }
                routeNodeCount.increment();
                var ancestry = new LinkedHashSet<>(task.ancestry());
                ancestry.add(task.sourcePath() + "#" + value(task.symbol()));
                enqueueChildren(route, task, ancestry, session, imports, queue, unresolvedEdges);
                pendingComponents.add(new PendingComponent(route, task));
            }
        }
        for (var pending : pendingComponents) {
            collectComponents(
                    pending.route(), pending.owner(), session, imports, components, unresolvedEdges
            );
        }

        var status = collections.isEmpty()
                ? GitLabFrontendCoverageStatus.BLOCKED
                : unresolvedEdges.value > 0 || session.limitReached()
                ? GitLabFrontendCoverageStatus.PARTIAL
                : GitLabFrontendCoverageStatus.READY;
        var limitations = session.diagnostics().stream()
                .map(GitLabFrontendGraphDiagnostic::message)
                .distinct()
                .toList();
        return new GitLabFrontendRouteSourceTraversalResult(
                scope,
                root,
                collections,
                List.copyOf(components.values()),
                new GitLabFrontendGraphCoverage(
                        status,
                        routeNodeCount.value,
                        session.routeFileCount(),
                        session.sourceReadCount(),
                        session.aliasResolutionCount(),
                        unresolvedEdges.value,
                        session.limitReached(),
                        limitations
                ),
                session.diagnostics()
        );
    }

    private void enqueueRoot(
            GitLabFrontendBootstrapRoot root,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            ArrayDeque<RouteTask> queue,
            Counter unresolvedEdges
    ) {
        var sourcePath = root.routerProviderSource().path();
        if (root.routeCollectionSymbol() == null) {
            queue.add(new RouteTask(
                    collectionId(sourcePath, null, null, GitLabFrontendRouteGraphEdgeKind.ROOT_ROUTES),
                    null, sourcePath, null, "", false, List.of(),
                    GitLabFrontendRouteGraphEdgeKind.ROOT_ROUTES, 0, Set.of()
            ));
            return;
        }
        var resolved = resolveLocalSymbol(
                sourcePath,
                root.routeCollectionSymbol(),
                session,
                imports,
                new LinkedHashSet<>(),
                0
        );
        if (resolved.size() != 1) {
            unresolvedEdges.increment();
            unresolvedDiagnostic(session, sourcePath, root.routeCollectionSymbol(), resolved.size());
            return;
        }
        var target = resolved.get(0);
        queue.add(new RouteTask(
                collectionId(target.sourcePath(), target.symbol(), null,
                        GitLabFrontendRouteGraphEdgeKind.ROOT_ROUTES),
                null, target.sourcePath(), target.symbol(), "", false, List.of(),
                GitLabFrontendRouteGraphEdgeKind.ROOT_ROUTES, 0, Set.of()
        ));
    }

    private void enqueueChildren(
            AngularRouteSourceParser.ParsedRoute route,
            RouteTask owner,
            Set<String> ancestry,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            ArrayDeque<RouteTask> queue,
            Counter unresolvedEdges
    ) {
        if (route.childrenDeclared() && route.childrenSymbol() != null) {
            var ownerRoute = routeKey(owner, route);
            enqueueSymbolTarget(
                    owner.sourcePath(), route.childrenSymbol(), route.fullPath(), route.lazy(), route.guards(),
                    GitLabFrontendRouteGraphEdgeKind.CHILDREN, owner.depth() + 1, ancestry, ownerRoute,
                    session, imports, queue, unresolvedEdges
            );
        }
        if (route.loadChildrenDeclared()) {
            var ownerRoute = routeKey(owner, route);
            enqueueDynamicTarget(
                    owner.sourcePath(), route.loadChildrenImportPath(), route.loadChildrenSymbol(),
                    route.fullPath(), true, route.guards(), GitLabFrontendRouteGraphEdgeKind.LOAD_CHILDREN,
                    owner.depth() + 1, ancestry, ownerRoute, session, imports, queue, unresolvedEdges
            );
        }
    }

    private void collectComponents(
            AngularRouteSourceParser.ParsedRoute route,
            RouteTask owner,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashMap<String, GitLabFrontendRouteSourceTraversalResult.ComponentTarget> components,
            Counter unresolvedEdges
    ) {
        if (route.componentSymbol() != null) {
            var targets = resolveLocalSymbol(
                    owner.sourcePath(), route.componentSymbol(), session, imports, new LinkedHashSet<>(), 0
            );
            addComponentTargets(
                    targets, route.componentSymbol(), route.fullPath(), GitLabFrontendRouteGraphEdgeKind.COMPONENT,
                    owner.sourcePath(), routeKey(owner, route), session, components, unresolvedEdges
            );
        }
        if (route.loadComponentDeclared()) {
            var targets = resolveDynamicSymbol(
                    owner.sourcePath(), route.loadComponentImportPath(), route.loadComponentSymbol(),
                    session, imports, new LinkedHashSet<>(), 0
            );
            addComponentTargets(
                    targets, route.loadComponentSymbol(), route.fullPath(),
                    GitLabFrontendRouteGraphEdgeKind.LOAD_COMPONENT, owner.sourcePath(), routeKey(owner, route), session,
                    components, unresolvedEdges
            );
        }
    }

    private void addComponentTargets(
            List<ResolvedSymbol> targets,
            String requestedSymbol,
            String routePath,
            GitLabFrontendRouteGraphEdgeKind relation,
            String sourcePath,
            GitLabFrontendRouteSourceTraversalResult.RouteKey ownerRoute,
            GitLabFrontendTargetedSourceSession session,
            LinkedHashMap<String, GitLabFrontendRouteSourceTraversalResult.ComponentTarget> components,
            Counter unresolvedEdges
    ) {
        if (targets.size() != 1) {
            unresolvedEdges.increment();
            unresolvedDiagnostic(session, sourcePath, requestedSymbol, targets.size());
            return;
        }
        var target = targets.get(0);
        var key = ownerRoute.collectionId() + "#" + ownerRoute.sourceOffset() + "|"
                + target.sourcePath() + "#" + target.symbol();
        components.putIfAbsent(key, new GitLabFrontendRouteSourceTraversalResult.ComponentTarget(
                ownerRoute, target.sourcePath(), target.symbol(), routePath, relation
        ));
    }

    private void enqueueSymbolTarget(
            String ownerPath,
            String symbol,
            String parentRoutePath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            GitLabFrontendRouteGraphEdgeKind relation,
            int depth,
            Set<String> ancestry,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentRoute,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            ArrayDeque<RouteTask> queue,
            Counter unresolvedEdges
    ) {
        var targets = resolveLocalSymbol(ownerPath, symbol, session, imports, new LinkedHashSet<>(), depth);
        enqueueResolvedTargets(
                targets, symbol, ownerPath, parentRoutePath, inheritedLazy, inheritedGuards,
                relation, depth, ancestry, parentRoute, session, queue, unresolvedEdges
        );
    }

    private void enqueueDynamicTarget(
            String ownerPath,
            String importPath,
            String symbol,
            String parentRoutePath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            GitLabFrontendRouteGraphEdgeKind relation,
            int depth,
            Set<String> ancestry,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentRoute,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            ArrayDeque<RouteTask> queue,
            Counter unresolvedEdges
    ) {
        var targets = resolveDynamicSymbol(
                ownerPath, importPath, symbol, session, imports, new LinkedHashSet<>(), depth
        );
        enqueueResolvedTargets(
                targets, symbol, ownerPath, parentRoutePath, inheritedLazy, inheritedGuards,
                relation, depth, ancestry, parentRoute, session, queue, unresolvedEdges
        );
    }

    private void enqueueResolvedTargets(
            List<ResolvedSymbol> targets,
            String requestedSymbol,
            String ownerPath,
            String parentRoutePath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            GitLabFrontendRouteGraphEdgeKind relation,
            int depth,
            Set<String> ancestry,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentRoute,
            GitLabFrontendTargetedSourceSession session,
            ArrayDeque<RouteTask> queue,
            Counter unresolvedEdges
    ) {
        if (targets.size() != 1) {
            unresolvedEdges.increment();
            unresolvedDiagnostic(session, ownerPath, requestedSymbol, targets.size());
            return;
        }
        var target = targets.get(0);
        queue.add(new RouteTask(
                collectionId(target.sourcePath(), target.symbol(), parentRoute, relation),
                parentRoute, target.sourcePath(), target.symbol(), parentRoutePath, inheritedLazy, inheritedGuards,
                relation, depth, Set.copyOf(ancestry)
        ));
    }

    private List<ResolvedSymbol> resolveDynamicSymbol(
            String ownerPath,
            String importPath,
            String symbol,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashSet<String> stack,
            int depth
    ) {
        if (importPath == null || symbol == null || !session.withinImportDepth(depth, ownerPath)) {
            return List.of();
        }
        var moduleTargets = imports.resolve(ownerPath, importPath);
        if (moduleTargets.size() != 1) {
            return List.of();
        }
        return resolveExportedSymbol(moduleTargets.get(0), symbol, session, imports, stack, depth + 1);
    }

    private List<ResolvedSymbol> resolveLocalSymbol(
            String sourcePath,
            String localSymbol,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashSet<String> stack,
            int depth
    ) {
        if (!session.withinImportDepth(depth, sourcePath)) {
            return List.of();
        }
        var source = session.readRequired(sourcePath);
        if (source == null) {
            return List.of();
        }
        var parsed = moduleParser.parse(sourcePath, source);
        if (declaresSymbol(parsed, source, localSymbol, false)) {
            return List.of(new ResolvedSymbol(sourcePath, localSymbol));
        }
        var binding = parsed.imported(localSymbol);
        if (binding == null) {
            return List.of();
        }
        var moduleTargets = imports.resolve(sourcePath, binding.moduleSpecifier());
        if (moduleTargets.size() != 1) {
            return List.of();
        }
        return resolveExportedSymbol(
                moduleTargets.get(0), binding.exportedName(), session, imports, stack, depth + 1
        );
    }

    private List<ResolvedSymbol> resolveExportedSymbol(
            String sourcePath,
            String exportedSymbol,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashSet<String> stack,
            int depth
    ) {
        if (!session.withinImportDepth(depth, sourcePath)) {
            return List.of();
        }
        var key = sourcePath + "#" + exportedSymbol;
        if (!stack.add(key)) {
            session.diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.IMPORT_CYCLE_DETECTED,
                    "A TypeScript re-export cycle was detected.",
                    sourcePath
            );
            return List.of();
        }
        try {
            var source = session.readRequired(sourcePath);
            if (source == null) {
                return List.of();
            }
            var parsed = moduleParser.parse(sourcePath, source);
            if ("default".equals(exportedSymbol)) {
                var defaultSymbol = defaultExportSymbol(source);
                if (defaultSymbol != null && declaresSymbol(parsed, source, defaultSymbol, false)) {
                    return List.of(new ResolvedSymbol(sourcePath, defaultSymbol));
                }
            }
            if (declaresSymbol(parsed, source, exportedSymbol, true)) {
                return List.of(new ResolvedSymbol(sourcePath, exportedSymbol));
            }
            var matches = new LinkedHashMap<String, ResolvedSymbol>();
            for (var reExport : parsed.namedReExports(exportedSymbol)) {
                resolveReExport(
                        sourcePath, reExport.moduleSpecifier(), reExport.sourceName(),
                        session, imports, stack, depth, matches
                );
            }
            for (var reExport : parsed.starReExports()) {
                resolveReExport(
                        sourcePath, reExport.moduleSpecifier(), exportedSymbol,
                        session, imports, stack, depth, matches
                );
            }
            return List.copyOf(matches.values());
        } finally {
            stack.remove(key);
        }
    }

    private void resolveReExport(
            String sourcePath,
            String moduleSpecifier,
            String sourceSymbol,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashSet<String> stack,
            int depth,
            LinkedHashMap<String, ResolvedSymbol> matches
    ) {
        var moduleTargets = imports.resolve(sourcePath, moduleSpecifier);
        if (moduleTargets.size() != 1) {
            return;
        }
        for (var match : resolveExportedSymbol(
                moduleTargets.get(0), sourceSymbol, session, imports, stack, depth + 1
        )) {
            matches.putIfAbsent(match.sourcePath() + "#" + match.symbol(), match);
        }
    }

    private boolean declaresSymbol(
            AngularBootstrapSourceParser.ParsedSource parsed,
            String source,
            String symbol,
            boolean requireExport
    ) {
        var constant = parsed.constant(symbol);
        if (constant != null && (!requireExport || constant.exported())) {
            return true;
        }
        var exportPrefix = requireExport ? "export\\s+" : "(?:export\\s+)?";
        return Pattern.compile(
                "(?s)\\b" + exportPrefix + "(?:default\\s+)?(?:class|function|enum)\\s+"
                        + Pattern.quote(symbol) + "\\b"
        ).matcher(source).find();
    }

    private String defaultExportSymbol(String source) {
        for (var pattern : List.of(
                NAMED_DEFAULT_DECLARATION,
                DEFAULT_IDENTIFIER_EXPORT,
                LOCAL_DEFAULT_EXPORT
        )) {
            var matcher = pattern.matcher(source);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private void unresolvedDiagnostic(
            GitLabFrontendTargetedSourceSession session,
            String sourcePath,
            String symbol,
            int matchCount
    ) {
        if (session.sourceReadBudgetExhausted()) {
            return;
        }
        session.diagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING,
                matchCount > 1
                        ? GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_AMBIGUOUS
                        : GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_NOT_FOUND,
                matchCount > 1
                        ? "A targeted TypeScript symbol resolved to more than one source."
                        : "A targeted TypeScript symbol or module could not be resolved: " + value(symbol) + ".",
                sourcePath
        );
    }

    private String value(String value) {
        return value != null ? value : "<inline>";
    }

    private GitLabFrontendRouteSourceTraversalResult.RouteKey routeKey(
            RouteTask task,
            AngularRouteSourceParser.ParsedRoute route
    ) {
        return new GitLabFrontendRouteSourceTraversalResult.RouteKey(task.collectionId(), route.sourceOffset());
    }

    private String collectionId(
            String sourcePath,
            String symbol,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentRoute,
            GitLabFrontendRouteGraphEdgeKind relation
    ) {
        var parent = parentRoute != null
                ? parentRoute.collectionId() + "#" + parentRoute.sourceOffset()
                : "root";
        return parent + "|" + relation + "|" + sourcePath + "#" + value(symbol);
    }

    private record ResolvedSymbol(String sourcePath, String symbol) {
    }

    private record PendingComponent(
            AngularRouteSourceParser.ParsedRoute route,
            RouteTask owner
    ) {
    }

    private record RouteTask(
            String collectionId,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentRoute,
            String sourcePath,
            String symbol,
            String parentRoutePath,
            boolean inheritedLazy,
            List<String> inheritedGuards,
            GitLabFrontendRouteGraphEdgeKind relation,
            int depth,
            Set<String> ancestry
    ) {
    }

    private static final class Counter {
        private int value;

        private void increment() {
            value++;
        }
    }
}
