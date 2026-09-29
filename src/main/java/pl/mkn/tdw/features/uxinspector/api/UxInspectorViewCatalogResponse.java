package pl.mkn.tdw.features.uxinspector.api;

import java.util.List;
import java.time.Instant;

public record UxInspectorViewCatalogResponse(
        String systemId,
        String systemLabel,
        Instant dataCollectedAt,
        SourceRevision sourceRevision,
        String status,
        List<ViewOption> views,
        List<Diagnostic> diagnostics,
        List<String> limitations
) {
    public UxInspectorViewCatalogResponse {
        views = views != null ? List.copyOf(views) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
    public record SourceRevision(String branch) {}
    public record ViewOption(String viewId, String label, String routePattern, List<String> componentSelectors,
                             String status, List<String> limitations) {
        public ViewOption {
            componentSelectors = componentSelectors != null ? List.copyOf(componentSelectors) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }
    public record Diagnostic(String severity, String code, String message, String sourcePath) {}
}
