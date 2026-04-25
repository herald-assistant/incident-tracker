package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CopilotMetricsLoggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotThrowWhenSummaryContainsMissingOptionalData() {
        var logger = new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);

        assertDoesNotThrow(() -> logger.logSummary(new CopilotAnalysisMetrics(
                null,
                null,
                null,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0,
                0,
                0L,
                false,
                false,
                false,
                null,
                null
        )));
    }

    @Test
    void shouldNotThrowWhenToolEventContainsMissingOptionalData() {
        var logger = new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);

        assertDoesNotThrow(() -> logger.logToolEvent(new CopilotToolMetrics(
                null,
                null,
                null,
                null,
                "unknown",
                0L,
                0L,
                false,
                false,
                false,
                false
        )));
    }
}
