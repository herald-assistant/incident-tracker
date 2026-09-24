package pl.mkn.tdw.features.uiexplorer.ai.chat;

import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record UiExplorerFollowUpChatRequest(
        String runReference,
        UiExplorerJobStartRequest initialRequest,
        UiExplorerScreenReachabilityContext context,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef,
        AnalysisReport report
) {
    public UiExplorerFollowUpChatRequest(String runReference, UiExplorerJobStartRequest initialRequest,
                                         UiExplorerScreenReachabilityContext context, String message,
                                         String copilotSessionId, AnalysisAiAuthRef authRef) {
        this(runReference, initialRequest, context, message, copilotSessionId, authRef, null);
    }
}
