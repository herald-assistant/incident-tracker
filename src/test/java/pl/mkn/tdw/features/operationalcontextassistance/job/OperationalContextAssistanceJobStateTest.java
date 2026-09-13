package pl.mkn.tdw.features.operationalcontextassistance.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalContextAssistanceJobStateTest {

    @Test
    void publishesOnlyStableActivityMetadataWithoutPromptReasoningOrToolPayload() {
        var sensitive = "private-source-content-and-reasoning";
        var state = new OperationalContextAssistanceJobState("job-1");
        state.activity(new AnalysisAiActivityEvent(
                "event-1", "parent-1", "user.message", "MESSAGE", "COMPLETED",
                sensitive, sensitive, "turn-1", "interaction-1", "tool-call-1", sensitive,
                Instant.parse("2026-09-13T10:00:00Z"),
                Map.of("contentPreview", sensitive, "reasoningTextPreview", sensitive,
                        "arguments", Map.of("secret", sensitive), "output", sensitive)
        ));

        var publicEvent = state.snapshot().aiActivityEvents().get(0);
        assertThat(publicEvent.eventId()).isEqualTo("event-1");
        assertThat(publicEvent.category()).isEqualTo("MESSAGE");
        assertThat(publicEvent.status()).isEqualTo("COMPLETED");
        assertThat(publicEvent.details()).isEmpty();
        assertThat(publicEvent.toolName()).isNull();
        assertThat(publicEvent.toString()).doesNotContain(sensitive);
    }

    @Test
    void exposesPinnedToolProgressAndNumericUsageWithoutToolArguments() {
        var state = new OperationalContextAssistanceJobState("job-2");
        state.activity(new AnalysisAiActivityEvent(
                "tool-start", null, "tool.execution_start", "TOOL", "STARTED",
                "raw title", "raw summary", null, null, "call-1", "gitlab_read_repository_file",
                Instant.parse("2026-09-13T10:00:00Z"),
                Map.of("arguments", Map.of("secret", "private-source-content"))
        ));
        state.activity(new AnalysisAiActivityEvent(
                "usage-1", null, "assistant.usage", "USAGE", "INFO",
                "raw title", "raw summary", null, null, null, null,
                Instant.parse("2026-09-13T10:01:00Z"),
                Map.of("model", "gpt-5.6-terra", "inputTokens", 100, "outputTokens", 20,
                        "contentPreview", "private-source-content")
        ));

        var events = state.snapshot().aiActivityEvents();
        assertThat(events.get(0).type()).isEqualTo("tool.execution_start");
        assertThat(events.get(0).toolName()).isEqualTo("gitlab_read_repository_file");
        assertThat(events.get(0).summary()).contains("gitlab_read_repository_file");
        assertThat(events.get(0).details()).isEmpty();
        assertThat(events.get(1).details()).containsEntry("inputTokens", 100)
                .containsEntry("outputTokens", 20);
        assertThat(events.get(1).summary()).contains("gpt-5.6-terra", "input 100", "output 20");
        assertThat(events.toString()).doesNotContain("private-source-content");
    }

    @Test
    void reportsPlannedToolCountWithoutExposingAssistantText() {
        var state = new OperationalContextAssistanceJobState("job-3");
        state.activity(new AnalysisAiActivityEvent(
                "message-1", null, "assistant.message", "MESSAGE", "COMPLETED",
                "secret title", "secret summary", null, null, null, null,
                Instant.parse("2026-09-13T10:00:00Z"),
                Map.of("toolRequestCount", 2, "contentPreview", "private-source-content")
        ));

        var published = state.snapshot().aiActivityEvents().get(0);
        assertThat(published.summary()).isEqualTo("Copilot zaplanował 2 wywołania narzędzi.");
        assertThat(published.details()).containsOnlyKeys("toolRequestCount");
        assertThat(published.toString()).doesNotContain("secret", "private-source-content");
    }
}
