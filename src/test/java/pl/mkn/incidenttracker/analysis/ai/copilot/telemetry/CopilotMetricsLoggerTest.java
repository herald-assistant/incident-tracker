package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityFinding;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityReport;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualitySeverity;

import java.util.List;

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
                null,
                null,
                false,
                null,
                true,
                0,
                List.of(),
                0,
                0,
                0,
                List.of()
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

    @Test
    void shouldNotThrowWhenQualityReportContainsFindings() {
        var logger = new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);

        assertDoesNotThrow(() -> logger.logQualityReport(
                "corr-123",
                new CopilotResponseQualityReport(
                        true,
                        CopilotResponseQualityProperties.Mode.REPORT_ONLY,
                        false,
                        List.of(new CopilotResponseQualityFinding(
                                CopilotResponseQualitySeverity.WARNING,
                                "SHALLOW_AFFECTED_FUNCTION",
                                "affectedFunction",
                                "affectedFunction is too shallow."
                        ))
                )
        ));
    }
}
