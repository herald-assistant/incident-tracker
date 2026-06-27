package pl.mkn.tdw.integrations.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.elasticsearch")
public class ElasticProperties {

    private String baseUrl;
    private String kibanaSpaceId = "default";
    private String indexPattern = "logs-*";
    private String authorizationHeader;
    private int evidenceSize = 20;
    private int evidenceMaxMessageCharacters = 2_000;
    private int evidenceMaxExceptionCharacters = 6_000;
    private int searchSize = 200;
    private int searchMaxMessageCharacters = 2_000;
    private int searchMaxExceptionCharacters = 6_000;
    private int httpSummarySize = 300;
    private int httpSummaryTimeWindowDays = 7;
    private int httpFetchSize = 50;
    private int httpFetchTimeWindowDays = 7;
    private int httpFetchSummaryMaxMessageCharacters = 500;
    private int httpFetchSummaryMaxExceptionCharacters = 1_000;
    private int httpFetchCompactMaxMessageCharacters = 2_000;
    private int httpFetchCompactMaxExceptionCharacters = 6_000;
    private int toolSize = 200;
    private int toolMaxMessageCharacters = 2_000;
    private int toolMaxExceptionCharacters = 6_000;

}
