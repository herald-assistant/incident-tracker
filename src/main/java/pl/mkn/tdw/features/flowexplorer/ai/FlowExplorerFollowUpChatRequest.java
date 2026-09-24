package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record FlowExplorerFollowUpChatRequest(
        FlowExplorerJobStartRequest initialRequest,
        FlowExplorerContextSnapshot contextSnapshot,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef,
        AnalysisReport report
) {

    public FlowExplorerFollowUpChatRequest {
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
    }

    public FlowExplorerFollowUpChatRequest(FlowExplorerJobStartRequest initialRequest,
                                           FlowExplorerContextSnapshot contextSnapshot, String message,
                                           String copilotSessionId, AnalysisAiAuthRef authRef) {
        this(initialRequest, contextSnapshot, message, copilotSessionId, authRef, null);
    }

    public FlowExplorerFollowUpChatRequest(
            FlowExplorerJobStartRequest initialRequest,
            FlowExplorerContextSnapshot contextSnapshot,
            String message
    ) {
        this(
                initialRequest,
                contextSnapshot,
                message,
                null,
                AnalysisAiAuthRef.localToken(null),
                null
        );
    }
}
