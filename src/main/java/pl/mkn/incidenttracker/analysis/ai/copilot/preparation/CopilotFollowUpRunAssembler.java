package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CopilotFollowUpRunAssembler {

    private static final String INCIDENT_TOOL_DENIED_MESSAGE =
            "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.";

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotArtifactService artifactService;
    private final CopilotFollowUpPromptRenderer promptRenderer;
    private final CopilotIncidentHiddenToolContextFactory hiddenToolContextFactory;

    public CopilotFollowUpRunAssembly assemble(AnalysisAiChatRequest request) {
        var toolSessionContext = buildToolSessionContext(request);
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext);
        var toolAccessPolicy = CopilotToolAccessPolicy.fromFollowUpSession(
                registeredTools,
                StringUtils.hasText(request.environment()),
                StringUtils.hasText(request.gitLabGroup()) && StringUtils.hasText(request.gitLabBranch())
        );
        var renderedArtifacts = artifactService.renderArtifacts(artifactRequest(request), toolAccessPolicy);
        var prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                toolSessionContext,
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                skillRuntimeLoader.resolveSkillDirectories(),
                request.options(),
                INCIDENT_TOOL_DENIED_MESSAGE
        );

        return new CopilotFollowUpRunAssembly(
                toolSessionContext,
                renderedArtifacts,
                prompt,
                new CopilotPreparedSessionRequest(
                        request.correlationId(),
                        prompt,
                        sessionConfigRequest,
                        artifactService.toArtifactContentMap(renderedArtifacts)
                )
        );
    }

    private CopilotToolSessionContext buildToolSessionContext(AnalysisAiChatRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-chat-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                hiddenToolContextFactory.fromChatRequest(request)
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
