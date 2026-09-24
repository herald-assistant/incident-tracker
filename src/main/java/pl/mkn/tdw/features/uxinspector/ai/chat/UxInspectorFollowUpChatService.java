package pl.mkn.tdw.features.uxinspector.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.uxinspector.ai.copilot.*;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorTargetToolSetFactory;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UxInspectorFollowUpChatService {
    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("ux-inspector");

    private final UxInspectorFollowUpPromptService promptService;
    private final UxInspectorCopilotToolSessionContextFactory contextFactory;
    private final UxInspectorTargetToolSetFactory targetToolSetFactory;
    private final CopilotSdkToolFactory toolFactory;
    private final CopilotRunAuthMapper authMapper;
    private final CopilotRunPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;

    public CopilotExecutionResult chat(UxInspectorFollowUpChatRequest request,
                                       AnalysisAiToolEvidenceListener evidenceListener,
                                       AnalysisAiActivityListener activityListener) {
        var prompt = promptService.prepare(request);
        var context = contextFactory.createFollowUp(request.runReference(), request.copilotSessionId(),
                request.context(), request.report());
        var targetTools = targetToolSetFactory.create(context.analysisRunId(), request.context());
        var registered = toolFactory.createToolDefinitions(context, DESCRIPTION_CONTEXT, targetTools.callbacks());
        var policy = UxInspectorCopilotToolAccessPolicy.forFollowUp(registered);
        if (!policy.followUpResearchAvailable()) {
            throw new IllegalStateException("UX Inspector follow-up research tools are unavailable.");
        }
        var config = new CopilotSessionConfigRequest(
                context.copilotSessionId(), policy.enabledTools(), policy.availableToolNames(),
                new CopilotModelSelection(request.initialRequest().model(), request.initialRequest().reasoningEffort()),
                "Use only scoped UX Inspector research and report tools.", false
        ).withDurableSystemInstructions(UxInspectorDurableSystemInstructions.followUp());
        var run = new CopilotRunRequest(
                context.analysisRunId(), authMapper.toRunAuth(request.authRef()),
                CopilotSessionTarget.existing(context.copilotSessionId()), prompt, config, Map.of(), null
        ).withInitialReport(request.report());
        var prepared = preparationService.prepare(run);
        if (evidenceListener != null && evidenceListener != AnalysisAiToolEvidenceListener.NO_OP) {
            prepared = prepared.withEvidenceSink(evidenceListener::onToolEvidenceUpdated);
        }
        if (activityListener != null && activityListener != AnalysisAiActivityListener.NO_OP) {
            prepared = prepared.withActivitySink(activityListener::onAiActivity);
        }
        var result = executionGateway.execute(prepared);
        if (result == null || !StringUtils.hasText(result.content())) {
            throw new IllegalStateException("UX Inspector follow-up completed without an answer.");
        }
        return result;
    }
}
