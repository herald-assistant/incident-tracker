package pl.mkn.tdw.aiplatform.copilot.tools.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotToolFeedbackToolsTest {

    @Test
    void shouldReturnAcceptedResultWithoutRecordingSideEffects() {
        var tools = new CopilotToolFeedbackTools();

        var result = tools.recordToolFeedback(
                "db_find_tables",
                "tool-call-1",
                "error",
                "no",
                "tool_error",
                "adapter_result",
                "medium",
                "Tool zwrócił błąd zamiast diagnostycznej informacji o tabelach.",
                "Pokazać błąd adaptera w prostszym kontrakcie."
        );

        assertEquals("accepted", result.status());
        assertFalse(result.feedbackId().isBlank());
        assertEquals("db_find_tables", result.targetToolName());
        assertEquals("tool-call-1", result.targetToolCallId());
        assertEquals("event_capture", result.resolution());
    }
}
