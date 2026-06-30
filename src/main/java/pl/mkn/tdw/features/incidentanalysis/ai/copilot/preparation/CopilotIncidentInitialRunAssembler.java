package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.report.CopilotIncidentReportFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

@Component
@RequiredArgsConstructor
public class CopilotIncidentInitialRunAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotIncidentArtifactService artifactService;
    private final CopilotIncidentToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotIncidentPromptRenderer promptRenderer;
    private final CopilotIncidentRunRequestFactory runRequestFactory;
    private final CopilotIncidentReportFactory reportFactory;

    public CopilotIncidentInitialRunAssembly assemble(InitialAnalysisRequest request) {
        var toolSessionContext = toolSessionContextFactory.fromInitialRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT);
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
                ).withInitialReport(reportFactory.createInitialReport(request, toolSessionContext))
        );
    }
}
