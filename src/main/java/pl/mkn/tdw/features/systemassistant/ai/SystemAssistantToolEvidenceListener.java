package pl.mkn.tdw.features.systemassistant.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.shared.evidence.*;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SystemAssistantToolEvidenceListener {
    private final CopilotToolEvidenceSessionStore store;
    @EventListener
    public void onFinished(CopilotToolInvocationFinishedEvent event) {
        if (event.sessionId() == null || !event.sessionId().startsWith("system-assistant-")
                || !event.toolName().startsWith("oa_")) return;
        var attributes = List.of(new AnalysisEvidenceAttribute("toolName", event.toolName()),
                new AnalysisEvidenceAttribute("toolCallId", event.toolCallId()),
                new AnalysisEvidenceAttribute("arguments", event.rawArguments()),
                new AnalysisEvidenceAttribute("result", event.rawResult() == null ? "Wywołanie zakończone błędem." : event.rawResult()),
                new AnalysisEvidenceAttribute("outcome", event.outcome().name()),
                new AnalysisEvidenceAttribute("latencyMs", Long.toString(event.latencyMs())));
        store.publishSection(event.sessionId(), event.toolName(), new AnalysisEvidenceSection("openapi", "call-" + event.toolCallId(),
                List.of(new AnalysisEvidenceItem(event.toolName(), attributes))));
    }
}
