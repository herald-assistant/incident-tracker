package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotMetricsLogger {

    private final CopilotMetricsProperties properties;
    private final ObjectMapper objectMapper;

    public void logSummary(CopilotAnalysisMetrics metrics) {
        if (!properties.isEnabled() || !properties.isLogSummary() || metrics == null) {
            return;
        }

        log.info("Copilot analysis metrics summary {}", toJson(metrics));
    }

    public void logToolEvent(CopilotToolMetrics metrics) {
        if (!properties.isEnabled() || !properties.isLogToolEvents() || metrics == null) {
            return;
        }

        log.info("Copilot tool metrics event {}", toJson(metrics));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            log.warn(
                    "Failed to serialize Copilot metrics payload type={} reason={}",
                    payload != null ? payload.getClass().getSimpleName() : "null",
                    exception.getMessage()
            );
            return String.valueOf(payload);
        }
    }
}
