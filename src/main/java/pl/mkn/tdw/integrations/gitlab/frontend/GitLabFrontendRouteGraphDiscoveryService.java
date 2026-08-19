package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabFrontendRouteGraphDiscoveryService {

    private static final Pattern ROUTE_PARAMETER = Pattern.compile(":([A-Za-z_$][A-Za-z0-9_$]*)");

    private final GitLabFrontendBootstrapDiscoveryService bootstrapDiscoveryService;
    private final GitLabFrontendRouteSourceTraversalService sourceTraversalService;
    private final GitLabRepositoryPort repositoryPort;

    public GitLabFrontendRouteGraph discover(
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits
    ) {
        var effectiveLimits = limits != null ? limits : GitLabFrontendGraphLimits.defaults();
        var bootstrap = bootstrapDiscoveryService.discover(scope, effectiveLimits);
        if (bootstrap.status() == GitLabFrontendCoverageStatus.BLOCKED) {
            return blockedGraph(bootstrap);
        }
        var traversal = sourceTraversalService.traverse(scope, bootstrap.root(), effectiveLimits);
        return build(traversal);
    }

    GitLabFrontendRouteGraph build(GitLabFrontendRouteSourceTraversalResult traversal) {
        var drafts = drafts(traversal);
        var nodeIds = new LinkedHashMap<GitLabFrontendRouteSourceTraversalResult.RouteKey, String>();
        for (var key : drafts.keySet()) {
            resolveNodeId(key, drafts, nodeIds, traversal.bootstrapRoot().rootId(), new LinkedHashSet<>());
        }

        var componentTargets = indexComponents(traversal.componentTargets());
        var lazyTargets = indexLazyTargets(traversal.routeCollections());
        var nodes = new ArrayList<GitLabFrontendRouteNode>();
        var edges = new ArrayList<GitLabFrontendRouteGraphEdge>();
        var rootNodeIds = new ArrayList<String>();
        for (var draft : drafts.values()) {
            var nodeId = nodeIds.get(draft.key());
            var parentNodeId = draft.parentKey() != null ? nodeIds.get(draft.parentKey()) : null;
            var component = componentTargets.get(draft.key());
            var viewTarget = component != null
                    ? new GitLabFrontendRouteTarget(component.symbol(), component.sourcePath())
                    : null;
            var lazy = lazyTargets.get(draft.key());
            var lazyTarget = lazy != null
                    ? new GitLabFrontendRouteTarget(lazy.symbol(), lazy.sourcePath())
                    : null;
            var limitations = limitations(draft, component, lazy);
            var kind = kind(draft.route(), viewTarget, limitations);
            var status = status(draft.route(), limitations);
            var screen = viewTarget != null
                    ? new GitLabFrontendScreenIdentity(
                            "screen-" + shortHash(nodeId + "|" + value(viewTarget.sourcePath())
                                    + "|" + value(viewTarget.symbol()) + "|" + draft.route().outlet()),
                            nodeId,
                            draft.route().fullPath(),
                            draft.route().outlet(),
                            viewTarget
                    )
                    : null;
            var routeSource = new GitLabFrontendSourceReference(
                    draft.route().sourcePath(),
                    draft.collectionSymbol(),
                    draft.route().sourceLine(),
                    draft.route().sourceEndLine()
            );
            var node = new GitLabFrontendRouteNode(
                    nodeId,
                    parentNodeId,
                    screen,
                    label(draft.route(), viewTarget),
                    draft.route().path(),
                    draft.route().fullPath(),
                    draft.route().outlet(),
                    kind,
                    status,
                    draft.route().lazy(),
                    routeParameters(draft.route().fullPath()),
                    viewTarget,
                    lazyTarget,
                    draft.route().redirectTo(),
                    draft.route().configuration(),
                    routeSource,
                    limitations
            );
            nodes.add(node);
            if (parentNodeId == null) {
                rootNodeIds.add(nodeId);
            }
            edges.add(routeEdge(draft, nodeId, parentNodeId, routeSource));
            if (viewTarget != null) {
                edges.add(targetEdge(nodeId, component.relation(), viewTarget, routeSource));
            } else if (draft.route().componentSymbol() != null || draft.route().loadComponentDeclared()) {
                edges.add(unresolvedTargetEdge(
                        nodeId,
                        draft.route().loadComponentDeclared()
                                ? GitLabFrontendRouteGraphEdgeKind.LOAD_COMPONENT
                                : GitLabFrontendRouteGraphEdgeKind.COMPONENT,
                        target(draft.route().loadComponentDeclared()
                                        ? draft.route().loadComponentSymbol()
                                        : draft.route().componentSymbol(),
                                draft.route().loadComponentImportPath()),
                        routeSource,
                        "The route view target could not be resolved statically."
                ));
            }
            if (draft.route().loadChildrenDeclared() && lazy == null) {
                edges.add(unresolvedTargetEdge(
                        nodeId,
                        GitLabFrontendRouteGraphEdgeKind.LOAD_CHILDREN,
                        target(draft.route().loadChildrenSymbol(), draft.route().loadChildrenImportPath()),
                        routeSource,
                        "The lazy route target could not be resolved statically."
                ));
            }
        }

        var chains = effectiveChains(nodes);
        var sourceRevision = sourceRevision(traversal.scope());
        var diagnostics = new ArrayList<>(traversal.diagnostics());
        addRevisionDiagnosticIfUnresolved(sourceRevision, diagnostics);
        return new GitLabFrontendRouteGraph(
                traversal.scope(),
                sourceRevision,
                traversal.bootstrapRoot(),
                rootNodeIds,
                nodes,
                edges,
                chains,
                List.of(),
                traversal.coverage(),
                diagnostics
        );
    }

    private GitLabFrontendSourceRevision sourceRevision(
            GitLabFrontendRepositoryScope scope
    ) {
        try {
            var revision = repositoryPort.resolveRevision(scope.group(), scope.projectName(), scope.ref());
            return new GitLabFrontendSourceRevision(scope.ref(), revision != null ? revision.commitId() : null);
        } catch (RuntimeException exception) {
            return new GitLabFrontendSourceRevision(scope.ref(), null);
        }
    }

    private void addRevisionDiagnosticIfUnresolved(
            GitLabFrontendSourceRevision sourceRevision,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        if (sourceRevision.commitId() != null) {
            return;
        }
        diagnostics.add(new GitLabFrontendGraphDiagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING,
                GitLabFrontendGraphDiagnosticCode.SOURCE_REVISION_UNRESOLVED,
                "The GitLab ref could not be resolved to an immutable source revision.",
                null,
                null,
                null
        ));
    }

    private GitLabFrontendRouteGraph blockedGraph(GitLabFrontendBootstrapDiscoveryResult bootstrap) {
        var limitations = bootstrap.diagnostics().stream()
                .map(GitLabFrontendGraphDiagnostic::message)
                .distinct()
                .toList();
        var sourceRevision = sourceRevision(bootstrap.scope());
        var diagnostics = new ArrayList<>(bootstrap.diagnostics());
        addRevisionDiagnosticIfUnresolved(sourceRevision, diagnostics);
        return new GitLabFrontendRouteGraph(
                bootstrap.scope(),
                sourceRevision,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.BLOCKED,
                        0,
                        0,
                        bootstrap.inspectedSourceCount(),
                        0,
                        0,
                        bootstrap.candidateLimitReached(),
                        limitations
                ),
                diagnostics
        );
    }

    private LinkedHashMap<GitLabFrontendRouteSourceTraversalResult.RouteKey, RouteDraft> drafts(
            GitLabFrontendRouteSourceTraversalResult traversal
    ) {
        var result = new LinkedHashMap<GitLabFrontendRouteSourceTraversalResult.RouteKey, RouteDraft>();
        var siblingOccurrences = new LinkedHashMap<String, Integer>();
        for (var collection : traversal.routeCollections()) {
            for (var route : collection.parsed().routes()) {
                var key = new GitLabFrontendRouteSourceTraversalResult.RouteKey(
                        collection.collectionId(), route.sourceOffset()
                );
                var parentKey = route.parentSourceOffset() != null
                        ? new GitLabFrontendRouteSourceTraversalResult.RouteKey(
                                collection.collectionId(), route.parentSourceOffset()
                        )
                        : collection.parentRoute();
                var siblingSignature = value(parentKey != null ? parentKey.collectionId() : null)
                        + "#" + (parentKey != null ? parentKey.sourceOffset() : -1)
                        + "|" + value(route.path())
                        + "|" + route.outlet()
                        + "|" + value(route.componentSymbol())
                        + "|" + value(route.loadComponentSymbol())
                        + "|" + value(route.loadChildrenSymbol())
                        + "|" + value(route.redirectTo());
                var siblingOccurrence = siblingOccurrences.getOrDefault(siblingSignature, 0);
                siblingOccurrences.put(siblingSignature, siblingOccurrence + 1);
                result.put(key, new RouteDraft(
                        key,
                        parentKey,
                        collection.symbol(),
                        parentKey != null && route.parentSourceOffset() != null
                                ? GitLabFrontendRouteGraphEdgeKind.CHILDREN
                                : collection.relation(),
                        route,
                        collection.parsed().limitations(),
                        siblingOccurrence
                ));
            }
        }
        return result;
    }

    private Map<GitLabFrontendRouteSourceTraversalResult.RouteKey,
            GitLabFrontendRouteSourceTraversalResult.ComponentTarget> indexComponents(
            List<GitLabFrontendRouteSourceTraversalResult.ComponentTarget> targets
    ) {
        var result = new LinkedHashMap<GitLabFrontendRouteSourceTraversalResult.RouteKey,
                GitLabFrontendRouteSourceTraversalResult.ComponentTarget>();
        for (var target : targets) {
            result.putIfAbsent(target.ownerRoute(), target);
        }
        return result;
    }

    private Map<GitLabFrontendRouteSourceTraversalResult.RouteKey,
            GitLabFrontendRouteSourceTraversalResult.RouteCollection> indexLazyTargets(
            List<GitLabFrontendRouteSourceTraversalResult.RouteCollection> collections
    ) {
        var result = new LinkedHashMap<GitLabFrontendRouteSourceTraversalResult.RouteKey,
                GitLabFrontendRouteSourceTraversalResult.RouteCollection>();
        for (var collection : collections) {
            if (collection.parentRoute() != null
                    && collection.relation() == GitLabFrontendRouteGraphEdgeKind.LOAD_CHILDREN) {
                result.putIfAbsent(collection.parentRoute(), collection);
            }
        }
        return result;
    }

    private String resolveNodeId(
            GitLabFrontendRouteSourceTraversalResult.RouteKey key,
            Map<GitLabFrontendRouteSourceTraversalResult.RouteKey, RouteDraft> drafts,
            Map<GitLabFrontendRouteSourceTraversalResult.RouteKey, String> resolved,
            String rootId,
            LinkedHashSet<GitLabFrontendRouteSourceTraversalResult.RouteKey> stack
    ) {
        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }
        var draft = drafts.get(key);
        if (draft == null || !stack.add(key)) {
            return null;
        }
        try {
            var parentNodeId = draft.parentKey() != null
                    ? resolveNodeId(draft.parentKey(), drafts, resolved, rootId, stack)
                    : null;
            var identity = rootId
                    + "|" + value(parentNodeId)
                    + "|" + value(draft.route().path())
                    + "|" + draft.route().outlet()
                    + "|" + value(draft.route().componentSymbol())
                    + "|" + value(draft.route().loadComponentSymbol())
                    + "|" + value(draft.route().loadChildrenSymbol())
                    + "|" + value(draft.route().redirectTo())
                    + "|" + draft.siblingOccurrence();
            var nodeId = "route-" + shortHash(identity);
            resolved.put(key, nodeId);
            return nodeId;
        } finally {
            stack.remove(key);
        }
    }

    private GitLabFrontendRouteGraphEdge routeEdge(
            RouteDraft draft,
            String nodeId,
            String parentNodeId,
            GitLabFrontendSourceReference source
    ) {
        return new GitLabFrontendRouteGraphEdge(
                "edge-" + shortHash(value(parentNodeId) + "|" + nodeId + "|" + draft.relation()),
                parentNodeId,
                nodeId,
                draft.relation(),
                GitLabFrontendRouteGraphEdgeStatus.RESOLVED,
                null,
                source,
                List.of()
        );
    }

    private GitLabFrontendRouteGraphEdge targetEdge(
            String nodeId,
            GitLabFrontendRouteGraphEdgeKind relation,
            GitLabFrontendRouteTarget target,
            GitLabFrontendSourceReference source
    ) {
        return new GitLabFrontendRouteGraphEdge(
                "edge-" + shortHash(nodeId + "|" + relation + "|" + value(target.sourcePath())),
                nodeId,
                null,
                relation,
                GitLabFrontendRouteGraphEdgeStatus.RESOLVED,
                target,
                source,
                List.of()
        );
    }

    private GitLabFrontendRouteGraphEdge unresolvedTargetEdge(
            String nodeId,
            GitLabFrontendRouteGraphEdgeKind relation,
            GitLabFrontendRouteTarget target,
            GitLabFrontendSourceReference source,
            String limitation
    ) {
        return new GitLabFrontendRouteGraphEdge(
                "edge-" + shortHash(nodeId + "|" + relation + "|unresolved|" + limitation),
                nodeId,
                null,
                relation,
                GitLabFrontendRouteGraphEdgeStatus.NOT_FOUND,
                target,
                source,
                List.of(limitation)
        );
    }

    private GitLabFrontendRouteTarget target(String symbol, String importPath) {
        return StringUtils.hasText(symbol) || StringUtils.hasText(importPath)
                ? new GitLabFrontendRouteTarget(symbol, importPath)
                : null;
    }

    private List<GitLabFrontendEffectiveRouteChain> effectiveChains(List<GitLabFrontendRouteNode> nodes) {
        var byId = new LinkedHashMap<String, GitLabFrontendRouteNode>();
        nodes.forEach(node -> byId.put(node.nodeId(), node));
        var result = new ArrayList<GitLabFrontendEffectiveRouteChain>();
        for (var node : nodes) {
            if (node.screen() == null) {
                continue;
            }
            var reverse = new ArrayList<GitLabFrontendRouteNode>();
            var current = node;
            var visited = new LinkedHashSet<String>();
            while (current != null && visited.add(current.nodeId())) {
                reverse.add(current);
                current = current.parentNodeId() != null ? byId.get(current.parentNodeId()) : null;
            }
            Collections.reverse(reverse);
            var parameters = new LinkedHashSet<String>();
            reverse.forEach(segment -> parameters.addAll(segment.routeParameters()));
            result.add(new GitLabFrontendEffectiveRouteChain(
                    node.screen(),
                    reverse.stream().map(this::segment).toList(),
                    List.copyOf(parameters)
            ));
        }
        return List.copyOf(result);
    }

    private GitLabFrontendRouteChainSegment segment(GitLabFrontendRouteNode node) {
        return new GitLabFrontendRouteChainSegment(
                node.nodeId(),
                node.pathSegment(),
                node.routePattern(),
                node.outlet(),
                node.configuration(),
                node.routeSource()
        );
    }

    private List<String> limitations(
            RouteDraft draft,
            GitLabFrontendRouteSourceTraversalResult.ComponentTarget component,
            GitLabFrontendRouteSourceTraversalResult.RouteCollection lazy
    ) {
        var result = new LinkedHashSet<String>();
        if (draft.route().pathDeclared() && draft.route().path() == null) {
            result.add("The route path is dynamic and could not be resolved statically.");
        }
        if ((draft.route().componentSymbol() != null || draft.route().loadComponentDeclared())
                && component == null) {
            result.add("The route view target could not be resolved statically.");
        }
        if (draft.route().loadChildrenDeclared() && lazy == null) {
            result.add("The lazy route target could not be resolved statically.");
        }
        result.addAll(draft.collectionLimitations());
        draft.route().configuration().stream()
                .flatMap(configuration -> configuration.limitations().stream())
                .forEach(result::add);
        return List.copyOf(result);
    }

    private GitLabFrontendRouteNodeKind kind(
            AngularRouteSourceParser.ParsedRoute route,
            GitLabFrontendRouteTarget viewTarget,
            List<String> limitations
    ) {
        if (route.redirectTo() != null) {
            return GitLabFrontendRouteNodeKind.REDIRECT;
        }
        if (viewTarget != null) {
            return GitLabFrontendRouteNodeKind.SCREEN;
        }
        if (!limitations.isEmpty() && (route.componentSymbol() != null
                || route.loadComponentDeclared() || route.loadChildrenDeclared())) {
            return GitLabFrontendRouteNodeKind.UNRESOLVED;
        }
        return GitLabFrontendRouteNodeKind.ROUTE;
    }

    private GitLabFrontendDiscoveryStatus status(
            AngularRouteSourceParser.ParsedRoute route,
            List<String> limitations
    ) {
        if (route.pathDeclared() && route.path() == null) {
            return GitLabFrontendDiscoveryStatus.UNSUPPORTED;
        }
        return limitations.isEmpty()
                ? GitLabFrontendDiscoveryStatus.RESOLVED
                : GitLabFrontendDiscoveryStatus.PARTIAL;
    }

    private String label(AngularRouteSourceParser.ParsedRoute route, GitLabFrontendRouteTarget viewTarget) {
        var title = route.configuration().stream()
                .filter(configuration -> configuration.kind() == GitLabFrontendRouteConfigurationKind.TITLE)
                .map(GitLabFrontendRouteConfiguration::staticValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (title != null) {
            return title;
        }
        if (viewTarget != null && viewTarget.symbol() != null) {
            return viewTarget.symbol();
        }
        return StringUtils.hasText(route.path()) ? route.path() : "/";
    }

    private List<String> routeParameters(String routePattern) {
        var result = new LinkedHashSet<String>();
        var matcher = ROUTE_PARAMETER.matcher(routePattern);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return List.copyOf(result);
    }

    private String shortHash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder();
            for (var index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String value(String value) {
        return value != null ? value : "";
    }

    private record RouteDraft(
            GitLabFrontendRouteSourceTraversalResult.RouteKey key,
            GitLabFrontendRouteSourceTraversalResult.RouteKey parentKey,
            String collectionSymbol,
            GitLabFrontendRouteGraphEdgeKind relation,
            AngularRouteSourceParser.ParsedRoute route,
            List<String> collectionLimitations,
            int siblingOccurrence
    ) {
    }
}
