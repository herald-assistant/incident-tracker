package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;

@Component
@RequiredArgsConstructor
public class DatabaseToolEvidenceCaptureListener {

    private final CopilotToolEvidenceSessionStore evidenceStore;
    private final DatabaseToolEvidenceMapper mapper;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (event.outcome() != CopilotToolInvocationOutcome.COMPLETED
                || !mapper.supports(event.toolName())
                || !StringUtils.hasText(event.sessionId())
                || !StringUtils.hasText(event.rawResult())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> {
            var updatedSection = mapper.capture(
                    event.toolCallId(),
                    event.toolName(),
                    event.rawArguments(),
                    event.rawResult(),
                    sessionEvidence
            );
            evidenceStore.publishSection(event.sessionId(), event.toolName(), updatedSection);
        });
    }
}
