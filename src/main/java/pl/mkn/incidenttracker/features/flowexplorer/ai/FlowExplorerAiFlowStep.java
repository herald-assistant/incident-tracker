package pl.mkn.incidenttracker.features.flowexplorer.ai;

import java.util.List;

public record FlowExplorerAiFlowStep(
        int order,
        String title,
        String plainLanguage,
        String technicalGrounding,
        List<String> sourceRefs
) {

    public FlowExplorerAiFlowStep {
        sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
    }
}
