package pl.mkn.tdw.features.uxinspector.ai.chat;

import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

public record UxInspectorFollowUpChatRequest(
        String runReference,
        UxInspectorJobStartRequest initialRequest,
        UxInspectorTargetContext context,
        String message,
        String copilotSessionId,
        AnalysisAiAuthRef authRef
) {
}
