package pl.mkn.tdw.features.uiexplorer.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerCopilotToolAccessPolicy;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerCopilotToolSessionContextFactory;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerDurableSystemInstructions;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UiExplorerFollowUpChatService {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("ui-explorer");
    private static final String DENIED = "Use only scoped UI Explorer read-only research tools.";

    private final UiExplorerFollowUpPromptService promptService;
    private final UiExplorerCopilotToolSessionContextFactory contextFactory;
    private final CopilotSdkToolFactory toolFactory;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotRunPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;

    public CopilotExecutionResult chat(
            UiExplorerFollowUpChatRequest request,
            AnalysisAiToolEvidenceListener evidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        var prompt = promptService.prepare(request);
        var toolContext = contextFactory.createFollowUp(
                request.runReference(), request.copilotSessionId(), request.initialRequest(), request.context());
        var registered = toolFactory.createToolDefinitions(toolContext, DESCRIPTION_CONTEXT);
        var policy = UiExplorerCopilotToolAccessPolicy.forFollowUp(registered);
        if (!policy.fallbackAvailable()) {
            throw new IllegalStateException("UI Explorer follow-up research tools are unavailable.");
        }
        var options = request.initialRequest().aiOptions();
        var sessionConfig = new CopilotSessionConfigRequest(
                toolContext.copilotSessionId(), policy.enabledTools(), policy.availableToolNames(),
                new CopilotModelSelection(options.model(), options.reasoningEffort()), DENIED
        ).withDurableSystemInstructions(UiExplorerDurableSystemInstructions.followUp());
        var runRequest = new CopilotRunRequest(
                toolContext.analysisRunId(), runAuthMapper.toRunAuth(request.authRef()),
                CopilotSessionTarget.existing(toolContext.copilotSessionId()), prompt,
                sessionConfig, Map.of(), null
        );
        var prepared = preparationService.prepare(runRequest);
        if (evidenceListener != null && evidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            prepared = prepared.withEvidenceSink(evidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            prepared = prepared.withActivitySink(activityListener::onAiActivity);
        }
        var result = executionGateway.execute(prepared);
        if (result == null || !StringUtils.hasText(result.content())) {
            throw new IllegalStateException("UI Explorer follow-up completed without an answer.");
        }
        return result;
    }
}
