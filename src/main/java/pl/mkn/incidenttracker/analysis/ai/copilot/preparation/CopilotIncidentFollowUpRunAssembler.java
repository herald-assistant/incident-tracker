package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;

@Component
@RequiredArgsConstructor
public class CopilotIncidentFollowUpRunAssembler {

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotFollowUpArtifactRequestFactory artifactRequestFactory;
    private final CopilotArtifactService artifactService;
    private final CopilotIncidentFollowUpPromptRenderer promptRenderer;
    private final CopilotIncidentRunRequestFactory runRequestFactory;

    public CopilotRunRequest assemble(AnalysisAiChatRequest request) {
        var toolSessionContext = toolSessionContextFactory.fromChatRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.createForFollowUp(request, registeredTools);
        var renderedArtifacts = artifactService.renderArtifacts(artifactRequestFactory.create(request), toolAccessPolicy);
        var prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        );

        return runRequestFactory.create(
                request.correlationId(),
                prompt,
                sessionConfigRequest,
                renderedArtifacts
        );
    }
}
