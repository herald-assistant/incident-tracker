package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.ai.copilot.metrics")
public class CopilotMetricsProperties {

    private boolean enabled = true;
    private boolean logSummary = true;
    private boolean logToolEvents = true;
}
