package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

public record FlowExplorerSectionRefineAiRequest(
        FlowExplorerJobStartRequest initialRequest,
        FlowExplorerContextSnapshot contextSnapshot,
        FlowExplorerResultResponse result,
        FlowExplorerResultSection targetSection,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {

    public FlowExplorerSectionRefineAiRequest {
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
    }
}
