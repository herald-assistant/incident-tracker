package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

@Component
@RequiredArgsConstructor
public class CopilotIncidentFollowUpRunAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotIncidentToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotIncidentRunRequestFactory runRequestFactory;

    public CopilotRunRequest assemble(AnalysisAiChatRequest request) {
        if (request == null || !StringUtils.hasText(request.copilotSessionId())) {
            throw new IllegalArgumentException("Copilot follow-up requires copilotSessionId for session resume.");
        }

        var toolSessionContext = toolSessionContextFactory.fromChatRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT);
        var toolAccessPolicy = toolAccessPolicyFactory.createForFollowUp(request, registeredTools);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        );
        var prompt = request.message() != null ? request.message().trim() : "";

        return runRequestFactory.create(
                request.correlationId(),
                request.authRef(),
                CopilotSessionTarget.existing(request.copilotSessionId()),
                prompt,
                sessionConfigRequest,
                java.util.List.of()
        );
    }
}
