package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.evidence.GitLabToolEvidenceMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotGitLabToolEvidenceSink;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ChangeVerificationCopilotToolInvocationEvidenceCaptureListener {

    private final CopilotToolEvidenceSessionStore evidenceStore;
    private final GitLabToolEvidenceMapper gitLabToolEvidenceMapper;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (!isChangeVerification(event)
                || event.outcome() != CopilotToolInvocationOutcome.COMPLETED
                || !gitLabToolEvidenceMapper.supports(event.toolName())
                || !StringUtils.hasText(event.sessionId())
                || !StringUtils.hasText(event.rawResult())) {
            return;
        }

        evidenceStore.sessionEvidence(event.sessionId()).ifPresent(sessionEvidence -> {
            var updatedSection = gitLabToolEvidenceMapper.capture(
                    event.toolCallId(),
                    event.toolName(),
                    event.rawArguments(),
                    event.rawResult(),
                    new CopilotGitLabToolEvidenceSink(sessionEvidence)
            );
            evidenceStore.publishSection(event.sessionId(), event.toolName(), updatedSection);
        });
    }

    private boolean isChangeVerification(CopilotToolInvocationFinishedEvent event) {
        if (event == null || event.sessionContext() == null || event.sessionContext().hiddenContext() == null) {
            return false;
        }
        return Objects.equals(
                ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE,
                event.sessionContext().hiddenContext().get(ChangeVerificationCopilotToolContextKeys.FEATURE)
        );
    }

}
