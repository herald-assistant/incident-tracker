package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.dynatrace")
public class DynatraceProperties {

    private String baseUrl;
    private String apiToken;
    private int entityPageSize = 200;
    private int entityFetchMaxPages = 5;
    private int entityCandidateLimit = 3;
    private int problemPageSize = 10;
    private int problemLimit = 3;
    private int problemEvidenceLimit = 3;
    private int metricEntityLimit = 2;
    private String metricResolution = "1m";
    private Duration queryPaddingBefore = Duration.ofMinutes(15);
    private Duration queryPaddingAfter = Duration.ofMinutes(15);

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiToken);
    }

}
