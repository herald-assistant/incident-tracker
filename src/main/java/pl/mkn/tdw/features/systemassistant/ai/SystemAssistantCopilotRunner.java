package pl.mkn.tdw.features.systemassistant.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotPatSource;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantProfileResources;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConversation;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantMessageRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantCopilotRunner implements SystemAssistantAiPort {
    private final CopilotPatSource patSource;
    private final CopilotSdkToolFactory sdkTools;
    private final OpenApiToolSetFactory tools;
    private final CopilotRunPreparationService preparation;
    private final CopilotSdkExecutionGateway gateway;
    private final SystemAssistantPrompt prompts;
    private final CopilotSessionCleanup cleanup;

    @Override
    public void requireReady() {
        if (patSource.currentPat() == null || patSource.currentPat().isBlank())
            throw new CopilotLocalTokenMissingException();
    }

    public CopilotRunRequest assemble(String turnId, SystemAssistantProfileResources profile,
                                      SystemAssistantConversation.PageContext page, List<AnalysisChatMessageResponse> history,
                                      SystemAssistantMessageRequest request, Consumer<AnalysisEvidenceSection> evidence,
                                      Consumer<AnalysisAiActivityEvent> activity) {
        var callbacks = tools.create(profile.executions());
        var names = callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
        var session = new CopilotToolSessionContext(turnId, "system-assistant-" + turnId, Map.of());
        var definitions = sdkTools.createToolDefinitions(session, CopilotToolDescriptionContext.profile("system-assistant"), callbacks)
                .stream().filter(tool -> names.contains(tool.name())).toList();
        var config = new CopilotSessionConfigRequest(session.copilotSessionId(), definitions, names,
                new CopilotModelSelection(request.model(), request.reasoningEffort()),
                "Używaj wyłącznie bieżących narzędzi OpenAPI tej tury.", false)
                .withDurableSystemInstructions(prompts.instructions(profile));
        return new CopilotRunRequest(turnId, CopilotRunAuth.localToken(), CopilotSessionTarget.newSession(),
                prompts.conversation(page, history), config, Map.of(), null, evidence, activity);
    }

    @Override
    public Reply answer(String turnId, SystemAssistantProfileResources profile,
                        SystemAssistantConversation.PageContext page, List<AnalysisChatMessageResponse> history,
                        SystemAssistantMessageRequest request, Consumer<AnalysisEvidenceSection> evidence,
                        Consumer<AnalysisAiActivityEvent> activity, List<Attachment> attachments) {
        var run = assemble(turnId, profile, page, history, request, evidence, activity);
        var prepared = preparation.prepare(run);
        prepared.messageOptions().setAttachments(SystemAssistantAttachments.prepare(attachments));
        try {
            var result = gateway.execute(prepared);
            return new Reply(result.content(), result.usage());
        } finally {
            cleanup.deleteSession(run.sessionConfigRequest().sessionId(), run.auth());
        }
    }
}
