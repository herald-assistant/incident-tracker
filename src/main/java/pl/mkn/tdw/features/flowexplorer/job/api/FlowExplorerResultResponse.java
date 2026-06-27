package pl.mkn.tdw.features.flowexplorer.job.api;

import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record FlowExplorerResultResponse(
        String status,
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        FlowExplorerAnalysisGoal goal,
        String prompt,
        FlowExplorerAiResponse aiResponse,
        AnalysisAiUsage usage
) {
}
