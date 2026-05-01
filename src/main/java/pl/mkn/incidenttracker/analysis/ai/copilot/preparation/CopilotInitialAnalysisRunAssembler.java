package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;

@Component
@RequiredArgsConstructor
public class CopilotInitialAnalysisRunAssembler {

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotArtifactService artifactService;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotPromptRenderer promptRenderer;
    private final CopilotIncidentRunRequestFactory runRequestFactory;

    public CopilotInitialAnalysisRunAssembly assemble(InitialAnalysisRequest request) {
        var toolSessionContext = toolSessionContextFactory.fromInitialRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.create(request, registeredTools);
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy);
        var prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        );

        return new CopilotInitialAnalysisRunAssembly(
                toolSessionContext,
                renderedArtifacts,
                prompt,
                runRequestFactory.create(
                        request.correlationId(),
                        prompt,
                        sessionConfigRequest,
                        renderedArtifacts
                )
        );
    }
}
