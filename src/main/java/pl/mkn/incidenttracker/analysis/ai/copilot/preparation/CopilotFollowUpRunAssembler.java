package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class CopilotFollowUpRunAssembler {

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotIncidentToolSessionContextFactory toolSessionContextFactory;
    private final CopilotIncidentSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotArtifactService artifactService;
    private final CopilotFollowUpPromptRenderer promptRenderer;

    public CopilotFollowUpRunAssembly assemble(AnalysisAiChatRequest request) {
        var toolSessionContext = toolSessionContextFactory.fromChatRequest(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.createForFollowUp(request, registeredTools);
        var renderedArtifacts = artifactService.renderArtifacts(artifactRequest(request), toolAccessPolicy);
        var prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var sessionConfigRequest = sessionConfigRequestFactory.create(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy,
                request.options()
        );

        return new CopilotFollowUpRunAssembly(
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

    private InitialAnalysisRequest artifactRequest(AnalysisAiChatRequest request) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.addAll(request.evidenceSections());
        sections.addAll(request.toolEvidenceSections());

        return new InitialAnalysisRequest(
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup(),
                sections,
                request.options()
        );
    }
}
