package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

public record FlowExplorerFollowUpChatRequest(
        FlowExplorerJobStartRequest initialRequest,
        FlowExplorerContextSnapshot contextSnapshot,
        FlowExplorerResultResponse result,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<FlowExplorerFollowUpChatTurn> history,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {

    public FlowExplorerFollowUpChatRequest {
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        history = history != null ? List.copyOf(history) : List.of();
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
    }

    public FlowExplorerFollowUpChatRequest(
            FlowExplorerJobStartRequest initialRequest,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerResultResponse result,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            List<FlowExplorerFollowUpChatTurn> history,
            String message
    ) {
        this(
                initialRequest,
                contextSnapshot,
                result,
                toolEvidenceSections,
                history,
                message,
                null,
                AnalysisAiAuthRef.localToken(null)
        );
    }
}
