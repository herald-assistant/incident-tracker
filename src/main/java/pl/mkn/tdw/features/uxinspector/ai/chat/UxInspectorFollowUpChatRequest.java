package pl.mkn.tdw.features.uxinspector.ai.chat;

import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record UxInspectorFollowUpChatRequest(
        String runReference,
        UxInspectorJobStartRequest initialRequest,
        UxInspectorTargetContext context,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef,
        AnalysisReport report
) {
    public UxInspectorFollowUpChatRequest(String runReference, UxInspectorJobStartRequest initialRequest,
                                          UxInspectorTargetContext context, String message,
                                          String copilotSessionId, AnalysisAiAuthRef authRef) {
        this(runReference, initialRequest, context, message, copilotSessionId, authRef, null);
    }
}
