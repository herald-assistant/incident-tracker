package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.MessageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CopilotSdkFollowUpPreparationService {

    private final CopilotSdkToolBridge toolBridge;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotArtifactService artifactService;
    private final CopilotFollowUpPromptRenderer promptRenderer;
    private final CopilotSessionConfigFactory sessionConfigFactory;

    public CopilotSdkPreparedRequest prepare(AnalysisAiChatRequest request) {
        var toolSessionContext = buildToolSessionContext(request);
        var registeredTools = toolBridge.buildToolDefinitions(toolSessionContext);
        var toolAccessPolicy = CopilotToolAccessPolicy.fromFollowUpSession(
                registeredTools,
                StringUtils.hasText(request.environment()),
                StringUtils.hasText(request.gitLabGroup()) && StringUtils.hasText(request.gitLabBranch())
        );
        var artifactRequest = artifactRequest(request);
        var renderedArtifacts = artifactService.renderArtifacts(artifactRequest, toolAccessPolicy);
        var skillDirectories = skillRuntimeLoader.resolveSkillDirectories();
        var clientOptions = sessionConfigFactory.clientOptions();
        var sessionConfig = sessionConfigFactory.sessionConfig(
                toolSessionContext,
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy,
                skillDirectories,
                request.options()
        );

        var prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var messageOptions = new MessageOptions().setPrompt(prompt);

        return new CopilotSdkPreparedRequest(
                request.correlationId(),
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactService.toArtifactContentMap(renderedArtifacts),
                artifactRequest
        );
    }

    private CopilotToolSessionContext buildToolSessionContext(AnalysisAiChatRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-chat-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
    }

    private AnalysisAiAnalysisRequest artifactRequest(AnalysisAiChatRequest request) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.addAll(request.evidenceSections());
        sections.addAll(request.toolEvidenceSections());

        return new AnalysisAiAnalysisRequest(
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup(),
                sections,
                request.options()
        );
    }
}
