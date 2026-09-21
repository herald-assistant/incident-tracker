package pl.mkn.tdw.frontendcatalog;

import java.util.List;

public record FrontendViewCatalog(
        String systemId,
        String systemLabel,
        SourceRevision sourceRevision,
        Status status,
        List<View> views,
        List<Diagnostic> diagnostics,
        List<String> limitations,
        Boundary boundary
) {
    public FrontendViewCatalog {
        views = views != null ? List.copyOf(views) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public enum Status { READY, PARTIAL, BLOCKED }
    public record SourceRevision(String branch, String revision) {}
    public record View(String viewId, String label, String routePattern, String parentRoutePattern,
                       List<String> componentSelectors, String status,
                       boolean lazyLoaded, List<String> guards, List<String> routeParameters, List<String> limitations) {
        public View {
            componentSelectors = componentSelectors != null ? List.copyOf(componentSelectors) : List.of();
            guards = guards != null ? List.copyOf(guards) : List.of();
            routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }
    public record Diagnostic(String severity, String code, String message, String sourcePath) {}
    public record Boundary(int visitedRouteNodeCount, int visitedRouteFileCount, int sourceReadCount,
                           int aliasResolutionCount, int unresolvedEdgeCount, boolean limitReached,
                           int maxRouteNodes, int maxRouteFiles, int maxSourceReads,
                           int maxAliasResolutions, int maxImportDepth) {}
}
