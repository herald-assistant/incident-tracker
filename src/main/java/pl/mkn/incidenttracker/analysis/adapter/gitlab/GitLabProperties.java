package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.gitlab")
public class GitLabProperties {

    private String baseUrl;
    private String group;
    private String token;
    private boolean ignoreSslErrors;
    private int searchResultsPerTerm = 20;
    private int maxCandidateCount = 10;

}
