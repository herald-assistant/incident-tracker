package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationCopilotToolInvocationEvidenceCaptureListenerTest {

    @Test
    void shouldCaptureChangeVerificationToolInvocationOutcome() {
        var evidenceStore = new CopilotToolEvidenceSessionStore();
        var listener = new ChangeVerificationCopilotToolInvocationEvidenceCaptureListener(evidenceStore);
        var captured = new ArrayList<AnalysisEvidenceSection>();
        evidenceStore.registerSession("session-1", captured::add);

        listener.onToolInvocationFinished(new CopilotToolInvocationFinishedEvent(
                changeVerificationContext(),
                "session-1",
                "call-1",
                "gitlab_read_file",
                "{\"projectName\":\"customer-api\"}",
                CopilotToolInvocationOutcome.REJECTED,
                "Rejected by Change Verification scope policy.",
                12L,
                null
        ));

        assertThat(captured).singleElement()
                .satisfies(section -> {
                    assertThat(section.provider()).isEqualTo("change-verification");
                    assertThat(section.category()).isEqualTo("ai-tool-invocations");
                    assertThat(section.items()).singleElement()
                            .satisfies(item -> {
                                assertThat(item.title()).isEqualTo("AI tool: gitlab_read_file -> REJECTED");
                                assertThat(item.attributes())
                                        .extracting("name")
                                        .contains("toolName", "toolCallId", "outcome", "arguments", "result");
                            });
                });
    }

    @Test
    void shouldIgnoreToolInvocationOutsideChangeVerification() {
        var evidenceStore = new CopilotToolEvidenceSessionStore();
        var listener = new ChangeVerificationCopilotToolInvocationEvidenceCaptureListener(evidenceStore);
        var captured = new ArrayList<AnalysisEvidenceSection>();
        evidenceStore.registerSession("session-1", captured::add);

        listener.onToolInvocationFinished(new CopilotToolInvocationFinishedEvent(
                new CopilotToolSessionContext("run-1", "session-1", Map.of("feature", "incident-analysis")),
                "session-1",
                "call-1",
                "gitlab_read_file",
                "{}",
                CopilotToolInvocationOutcome.COMPLETED,
                "{}",
                12L,
                null
        ));

        assertThat(captured).isEmpty();
    }

    private CopilotToolSessionContext changeVerificationContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "session-1",
                Map.of(ChangeVerificationCopilotToolContextKeys.FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE)
        );
    }
}
