package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;

import java.util.List;

public record FlowExplorerResultResponse(
        String status,
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        String userIntentSummary,
        String audienceSummary,
        String confidence,
        List<String> visibilityLimits,
        String prompt,
        FlowExplorerAiResponse aiResponse,
        AnalysisAiUsage usage
) {

    public FlowExplorerResultResponse {
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
