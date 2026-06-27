package pl.mkn.tdw.features.flowexplorer.ai;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

public record FlowExplorerFollowUpChatRequest(
        FlowExplorerJobStartRequest initialRequest,
        FlowExplorerContextSnapshot contextSnapshot,
        FlowExplorerResultResponse result,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<FlowExplorerFollowUpChatTurn> history,
        String message
) {

    public FlowExplorerFollowUpChatRequest {
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        history = history != null ? List.copyOf(history) : List.of();
    }
}
