package pl.mkn.tdw.features.operationalcontextassistance.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.operational-context-assistance.ai")
public class OperationalContextAssistanceAiProperties {

    /** Blank inherits the platform's configured model. */
    private String model;
    private String reasoningEffort = "high";
}
