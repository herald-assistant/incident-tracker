package pl.mkn.tdw.features.flowexplorer.job.state;

import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

public record FlowExplorerResultUpdateDecisionContext(
        String assistantMessageId,
        FlowExplorerResultUpdateDecision decision,
        FlowExplorerAiResponse authoritativeResult,
        FlowExplorerJobStartRequest initialRequest,
        FlowExplorerContextSnapshot contextSnapshot,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {
}
