package pl.mkn.incidenttracker.analysis;

import java.util.List;

public record AnalysisFlowDiagramNode(
        String id,
        String kind,
        String title,
        String componentName,
        String factStatus,
        String firstSeenAt,
        List<AnalysisFlowDiagramMetadata> metadata,
        boolean errorSource
) {
}
