package pl.mkn.tdw.features.deliverycomplexityassessment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "delivery-complexity-assessment")
public class DeliveryComplexityAssessmentProperties {

    private boolean enabled = true;
    private String timeZone = "Europe/Warsaw";
    private int maxRangeDays = 92;
    private int maxIssuesPerJob = 200;
    private int jiraPageSize = 50;
    private String jiraDoneStatusId = "Done";
    private String jiraTeamFieldId = "";
    private int maxParallelSourceRequests = 4;
    private int maxParallelAnalyses = 4;
    private int maxMergeRequestsPerIssue = 20;
    private Duration itemTimeout = Duration.ofMinutes(5);
}
