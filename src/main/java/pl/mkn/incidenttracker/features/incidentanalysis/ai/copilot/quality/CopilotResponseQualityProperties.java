package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.quality;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityMode;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.ai.copilot.quality-gate")
public class CopilotResponseQualityProperties {

    private boolean enabled = true;
    private CopilotResponseQualityMode mode = CopilotResponseQualityMode.REPORT_ONLY;
    private int minAffectedFunctionCharacters = 80;
    private int highConfidenceVisibilityLimitThreshold = 2;
}
