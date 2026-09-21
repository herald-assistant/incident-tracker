package pl.mkn.tdw.features.uiexplorer.ai.chat;

import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record UiExplorerFollowUpChatRequest(
        String runReference,
        UiExplorerJobStartRequest initialRequest,
        UiExplorerScreenReachabilityContext context,
        AnalysisReport report,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {
}
