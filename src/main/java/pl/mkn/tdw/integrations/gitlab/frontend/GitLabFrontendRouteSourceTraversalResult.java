package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;
import java.util.Objects;

record GitLabFrontendRouteSourceTraversalResult(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendBootstrapRoot bootstrapRoot,
        List<RouteCollection> routeCollections,
        List<ComponentTarget> componentTargets,
        GitLabFrontendGraphCoverage coverage,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {

    GitLabFrontendRouteSourceTraversalResult {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        bootstrapRoot = Objects.requireNonNull(bootstrapRoot, "bootstrapRoot must not be null");
        routeCollections = routeCollections != null ? List.copyOf(routeCollections) : List.of();
        componentTargets = componentTargets != null ? List.copyOf(componentTargets) : List.of();
        coverage = Objects.requireNonNull(coverage, "coverage must not be null");
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    record RouteCollection(
            String collectionId,
            RouteKey parentRoute,
            String sourcePath,
            String symbol,
            String parentRoutePath,
            GitLabFrontendRouteGraphEdgeKind relation,
            AngularRouteSourceParser.ParseResult parsed
    ) {
    }

    record ComponentTarget(
            RouteKey ownerRoute,
            String sourcePath,
            String symbol,
            String routePath,
            GitLabFrontendRouteGraphEdgeKind relation
    ) {
    }

    record RouteKey(String collectionId, int sourceOffset) {
    }
}
