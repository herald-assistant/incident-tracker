package pl.mkn.tdw.features.deliveryeffectivenessassessment;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentIssue;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentIssueSource;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.jira.JiraConfluencePage;
import pl.mkn.tdw.integrations.jira.JiraIssueComment;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.time.Instant;
import java.util.List;

public final class DeliveryAssessmentTestFixtures {

    private DeliveryAssessmentTestFixtures() {
    }

    public static JiraIssueMaterial material(String key) {
        return new JiraIssueMaterial(
                key,
                "https://jira.example.com/browse/" + key,
                "Deliver customer status",
                "Expose customer status and preserve the existing API contract.",
                "Story",
                "Done",
                List.of("release"),
                List.of("Status is returned for active customers."),
                List.of(),
                List.of(),
                null,
                List.of(new JiraConfluencePage(
                        "11", "Functional design", "https://confluence.example.com/11",
                        "Customer status follows the eligibility decision.", "2", List.of()
                )),
                List.of(new JiraIssueComment("jira-comment-author-1", "2026-07-10", "internal comment payload")),
                List.of()
        );
    }

    public static DeliveryAssessmentIssue issue(String key) {
        return new DeliveryAssessmentIssue(
                key,
                Instant.parse("2026-07-10T10:00:00Z"),
                material(key),
                List.of()
        );
    }

    public static GitLabMergeRequest mergeRequest(long id, String path, String diff) {
        return new GitLabMergeRequest(
                id,
                id,
                77L,
                "crm/customer-api",
                "CRM-123 customer status",
                "merged",
                "https://gitlab.example.com/crm/customer-api/-/merge_requests/" + id,
                "feature/CRM-123",
                "main",
                "mr-author-101",
                101L,
                "2026-07-08T10:00:00Z",
                "2026-07-10T10:00:00Z",
                "2026-07-10T09:00:00Z",
                "1",
                List.of(),
                List.of(new GitLabMergeRequestChangedFile(path, path, false, false, false, diff)),
                List.of()
        );
    }

    public static DeliveryAssessmentIssueSource source(String key, GitLabMergeRequest... mergeRequests) {
        return new DeliveryAssessmentIssueSource(issue(key), List.of(mergeRequests), List.of());
    }

    public static DeliveryUnit unit(String key, GitLabMergeRequest... mergeRequests) {
        return new DeliveryUnit(
                "DU-" + key,
                List.of(issue(key)),
                List.of(mergeRequests),
                List.of()
        );
    }
}
