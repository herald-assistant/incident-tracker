package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotModelSelection;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CopilotInitialAnalysisRunAssembler {

    private static final String INCIDENT_TOOL_DENIED_MESSAGE =
            "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session.";

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
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
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                skillRuntimeLoader.resolveSkillDirectories(),
                modelSelection(request.options()),
                INCIDENT_TOOL_DENIED_MESSAGE
        );

        return new CopilotInitialAnalysisRunAssembly(
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

    private CopilotToolSessionContext buildToolSessionContext(InitialAnalysisRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                hiddenToolContextFactory.fromInitialRequest(request)
        );
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
