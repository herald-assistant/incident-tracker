package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadiness;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UiExplorerCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("ui-explorer");
    private static final String DENIED_TOOL_MESSAGE =
            "Use only UI Explorer skills and explicitly enabled, scoped GitLab fallback tools.";

    private final CopilotSdkToolFactory toolFactory;
    private final UiExplorerCopilotToolSessionContextFactory contextFactory;
    private final CopilotRunAuthMapper runAuthMapper;

    public UiExplorerCopilotRunAssembly assemble(
            String runReference,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            UiExplorerPromptPreparation preparation,
            UiExplorerAiReadiness readiness,
            AnalysisAiAuthRef authRef
    ) {
        if (readiness == null || !readiness.executable()) {
            throw new IllegalArgumentException("UI Explorer AI readiness must allow execution.");
        }
        var toolContext = contextFactory.create(runReference, context);
        var registeredTools = toolFactory.createToolDefinitions(toolContext, DESCRIPTION_CONTEXT);
        var accessPolicy = UiExplorerCopilotToolAccessPolicy.fromRegisteredTools(
                registeredTools,
                readiness.fallbackToolsRequired()
        );
        var sessionConfig = new CopilotSessionConfigRequest(
                toolContext.copilotSessionId(),
                accessPolicy.enabledTools(),
                accessPolicy.availableToolNames(),
                new CopilotModelSelection(request.model(), request.reasoningEffort()),
                DENIED_TOOL_MESSAGE
        ).withDurableSystemInstructions(UiExplorerDurableSystemInstructions.render(preparation))
                .withContextTierPreference(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        var runRequest = new CopilotRunRequest(
                toolContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfig,
                preparation.artifactContents(),
                null
        );
        return new UiExplorerCopilotRunAssembly(runRequest, toolContext, accessPolicy, readiness);
    }
}
