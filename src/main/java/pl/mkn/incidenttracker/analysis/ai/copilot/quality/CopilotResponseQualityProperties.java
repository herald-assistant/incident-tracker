package pl.mkn.incidenttracker.analysis.ai.copilot.quality;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.ai.copilot.quality-gate")
public class CopilotResponseQualityProperties {

    public enum Mode {
        REPORT_ONLY,
        SOFT_REPAIR,
        STRICT_FALLBACK
    }

    private boolean enabled = true;
    private Mode mode = Mode.REPORT_ONLY;
    private int minAffectedFunctionCharacters = 80;
    private int highConfidenceVisibilityLimitThreshold = 2;
}
