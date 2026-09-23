package pl.mkn.tdw.features.uiexplorer.ai.chat;

import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

public record UiExplorerFollowUpChatRequest(
        String runReference,
        UiExplorerJobStartRequest initialRequest,
        UiExplorerScreenReachabilityContext context,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {
}
