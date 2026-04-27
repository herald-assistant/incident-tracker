package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.MessageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CopilotSdkPreparationService {

    private final CopilotSdkToolBridge toolBridge;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotArtifactService artifactService;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotPromptRenderer promptRenderer;
    private final CopilotSessionConfigFactory sessionConfigFactory;
    private final CopilotSessionMetricsRegistry metricsRegistry;

    public CopilotSdkPreparedRequest prepare(AnalysisAiAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var toolSessionContext = buildToolSessionContext(request);
        var registeredTools = toolBridge.buildToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.create(request, registeredTools);
        var tools = toolAccessPolicy.enabledTools();
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy);
        var skillDirectories = skillRuntimeLoader.resolveSkillDirectories();
        var clientOptions = sessionConfigFactory.clientOptions();
        var sessionConfig = sessionConfigFactory.sessionConfig(
                toolSessionContext,
                tools,
                toolAccessPolicy,
                skillDirectories,
                request.options()
        );

        String prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var artifactContents = artifactService.toArtifactContentMap(renderedArtifacts);
        var messageOptions = new MessageOptions().setPrompt(prompt);
        metricsRegistry.recordPreparation(
                toolSessionContext,
                request,
                renderedArtifacts,
                prompt,
                (System.nanoTime() - preparationStart) / 1_000_000
        );

        return new CopilotSdkPreparedRequest(
                request.correlationId(),
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                request
        );
    }

    private CopilotToolSessionContext buildToolSessionContext(AnalysisAiAnalysisRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
    }

}
