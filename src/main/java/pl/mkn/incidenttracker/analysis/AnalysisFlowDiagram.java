package pl.mkn.incidenttracker.analysis;

import java.util.List;

public record AnalysisFlowDiagram(
        List<AnalysisFlowDiagramNode> nodes,
        List<AnalysisFlowDiagramEdge> edges
) {
}
