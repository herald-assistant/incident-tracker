package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.evidence.GitLabToolEvidenceMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.common.JsonPayloadReader;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationCopilotToolInvocationEvidenceCaptureListenerTest {

    @Test
    void shouldPersistImmutableDiffWithItsRevisionProvenance() {
        var store = new CopilotToolEvidenceSessionStore();
        var captured = new ArrayList<AnalysisEvidenceSection>();
        store.registerSession("session-1", captured::add);
        var raw = "{\"versionId\":11,\"baseSha\":\"%s\",\"headSha\":\"%s\",\"files\":[{\"patch\":\"-old +new\"}]}"
                .formatted("a".repeat(40), "c".repeat(40));
        listener(store).onToolInvocationFinished(new CopilotToolInvocationFinishedEvent(changeVerificationContext(),
                "session-1", "call-diff", "gitlab_read_merge_request_diff",
                "{\"projectName\":\"customer-api\",\"mergeRequestIid\":7,\"reason\":\"Porownuje rewizje.\"}",
                CopilotToolInvocationOutcome.COMPLETED, raw, 1L, null));
        assertThat(captured).singleElement().satisfies(section -> {
            assertThat(section.category()).isEqualTo("tool-fetched-diff");
            assertThat(section.items().get(0).attributes()).anySatisfy(attribute -> {
                assertThat(attribute.name()).isEqualTo("content");
                assertThat(attribute.value()).isEqualTo(raw);
            });
        });
    }

    @Test
    void shouldCaptureChangeVerificationGitLabToolEvidenceUsingSharedGitLabShape() {
        var evidenceStore = new CopilotToolEvidenceSessionStore();
        var listener = listener(evidenceStore);
        var captured = new ArrayList<AnalysisEvidenceSection>();
        evidenceStore.registerSession("session-1", captured::add);

        listener.onToolInvocationFinished(new CopilotToolInvocationFinishedEvent(
                changeVerificationContext(),
                "session-1",
                "call-1",
                "gitlab_search_repository_candidates",
                "{\"projectNames\":[\"customer-api\"],\"reason\":\"Locate endpoint\"}",
                CopilotToolInvocationOutcome.COMPLETED,
                "{\"candidates\":[]}",
                12L,
                null
        ));

        assertThat(captured).singleElement()
                .satisfies(section -> {
                    assertThat(section.provider()).isEqualTo("gitlab");
                    assertThat(section.category()).isEqualTo("tool-discovery");
                    assertThat(section.items()).singleElement()
                            .satisfies(item -> {
                                assertThat(item.title()).isEqualTo("GitLab search candidates");
                                assertThat(item.attributes())
                                        .extracting("name")
                                        .contains("toolName", "toolCallId", "toolArguments", "candidateCount");
                            });
                });
    }

    @Test
    void shouldIgnoreToolInvocationOutsideChangeVerification() {
        var evidenceStore = new CopilotToolEvidenceSessionStore();
        var listener = listener(evidenceStore);
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

    private ChangeVerificationCopilotToolInvocationEvidenceCaptureListener listener(
            CopilotToolEvidenceSessionStore evidenceStore
    ) {
        var objectMapper = new ObjectMapper();
        return new ChangeVerificationCopilotToolInvocationEvidenceCaptureListener(
                evidenceStore,
                new GitLabToolEvidenceMapper(objectMapper, new JsonPayloadReader(objectMapper))
        );
    }

    private CopilotToolSessionContext changeVerificationContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "session-1",
                Map.of(ChangeVerificationCopilotToolContextKeys.FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE)
        );
    }
}
