package pl.mkn.tdw.features.deliveryeffectivenessassessment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "delivery-effectiveness-assessment")
public class DeliveryEffectivenessAssessmentProperties {

    private boolean enabled = true;
    private String timeZone = "Europe/Warsaw";
    private int maxRangeDays = 92;
    private int maxIssuesPerJob = 200;
    private int jiraPageSize = 50;
    private int maxParallelAnalyses = 4;
    private int maxMergeRequestsPerIssue = 20;
    private int maxIssuesPerUnit = 10;
    private int maxMergeRequestsPerUnit = 20;
    private int maxChangedFilesPerMergeRequest = 300;
    private int maxJiraDescriptionCharacters = 6_000;
    private int maxMergeRequestDescriptionCharacters = 3_000;
    private int maxDiffCharactersPerUnit = 50_000;
    private int maxDocumentsPerUnit = 2;
    private int maxDocumentCharactersPerUnit = 8_000;
    private Duration itemTimeout = Duration.ofMinutes(5);
}
