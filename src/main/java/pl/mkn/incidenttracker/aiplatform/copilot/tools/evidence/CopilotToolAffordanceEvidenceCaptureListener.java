package pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;

@Component
@RequiredArgsConstructor
public class CopilotToolAffordanceEvidenceCaptureListener {

    private final CopilotToolEvidenceSessionStore evidenceStore;
    private final CopilotToolAffordanceEvidenceMapper evidenceMapper;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (event == null
                || event.outcome() != CopilotToolInvocationOutcome.COMPLETED
                || !StringUtils.hasText(event.sessionId())
                || !StringUtils.hasText(event.toolName())
                || !StringUtils.hasText(event.rawResult())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> {
            var section = evidenceMapper.capture(
                    event.toolCallId(),
                    event.toolName(),
                    event.rawArguments(),
                    event.rawResult(),
                    sessionEvidence
            );
            evidenceStore.publishSection(event.sessionId(), event.toolName(), section);
        });
    }
}
