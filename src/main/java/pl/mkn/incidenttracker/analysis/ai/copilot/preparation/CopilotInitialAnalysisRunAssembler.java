package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CopilotInitialAnalysisRunAssembler {

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotArtifactService artifactService;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotPromptRenderer promptRenderer;
    private final CopilotIncidentHiddenToolContextFactory hiddenToolContextFactory;

    public CopilotInitialAnalysisRunAssembly assemble(InitialAnalysisRequest request) {
        var toolSessionContext = buildToolSessionContext(request);
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
                new CopilotRunRequest(
                        request.correlationId(),
                        prompt,
                        sessionConfigRequest,
                        artifactService.toArtifactContentMap(renderedArtifacts),
                        null
                )
        );
    }

    private CopilotToolSessionContext buildToolSessionContext(InitialAnalysisRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                hiddenToolContextFactory.fromInitialRequest(request)
        );
    }
}
