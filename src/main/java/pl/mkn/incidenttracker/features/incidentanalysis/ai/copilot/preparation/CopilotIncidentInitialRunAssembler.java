package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

@Component
@RequiredArgsConstructor
public class CopilotIncidentInitialRunAssembler {

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotIncidentArtifactService artifactService;
    private final CopilotIncidentToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotIncidentPromptRenderer promptRenderer;
    private final CopilotIncidentRunRequestFactory runRequestFactory;

    public CopilotIncidentInitialRunAssembly assemble(InitialAnalysisRequest request) {
        var toolSessionContext = toolSessionContextFactory.fromInitialRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.create(request, registeredTools);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        );
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy, sessionConfigRequest);
        var prompt = promptRenderer.render(request, toolAccessPolicy, sessionConfigRequest, renderedArtifacts);

        return new CopilotIncidentInitialRunAssembly(
                runRequestFactory.create(
                        request.correlationId(),
                        request.authRef(),
                        prompt,
                        sessionConfigRequest,
                        renderedArtifacts
                )
        );
    }
}
