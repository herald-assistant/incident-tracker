package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.jira.JiraIssueCustomField;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterialRequest;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchItem;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchPort;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchRequest;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchResult;
import pl.mkn.tdw.integrations.jira.JiraIssueStatusHistory;
import pl.mkn.tdw.integrations.jira.JiraIssueStatusHistoryPort;
import pl.mkn.tdw.integrations.jira.JiraIssueStatusTransition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.material;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;

class DeliveryAssessmentSourceDiscoveryServiceTest {

    private final JiraIssueSearchPort searchPort = mock(JiraIssueSearchPort.class);
    private final JiraIssuePort issuePort = mock(JiraIssuePort.class);
    private final JiraIssueStatusHistoryPort historyPort = mock(JiraIssueStatusHistoryPort.class);
    private final GitLabRepositoryPort gitLabPort = mock(GitLabRepositoryPort.class);
    private DeliveryEffectivenessAssessmentProperties properties;
    private DeliveryAssessmentSourceDiscoveryService service;

    @BeforeEach
    void setUp() {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("crm/runtime");
        properties = new DeliveryEffectivenessAssessmentProperties();
        properties.setTimeZone("Europe/Warsaw");
        service = new DeliveryAssessmentSourceDiscoveryService(
                searchPort, issuePort, historyPort, gitLabPort, gitLabProperties, properties
        );
    }

    @Test
    void shouldUseTypedJiraSearchAndAcceptInclusiveWarsawStartBoundary() {
        var request = request("2026-07-02", "2026-07-02");
        when(searchPort.searchIssues(any())).thenReturn(new JiraIssueSearchResult(
                "effective-jql", 1, false,
                List.of(new JiraIssueSearchItem("CRM-1", "Done", "done", null)),
                List.of()
        ));
        when(historyPort.getStatusHistory("CRM-1")).thenReturn(history(
                transition("2026-07-01T22:00:00Z", "done")
        ));
        when(issuePort.getIssueMaterial(any(JiraIssueMaterialRequest.class))).thenReturn(material("CRM-1"));
        when(gitLabPort.findMergeRequestsByIssueKey("crm/runtime", "CRM-1", 20))
                .thenReturn(new GitLabMergeRequestSearchResult(
                        "CRM-1", "crm/runtime",
                        List.of(mergeRequest(7, "src/CustomerStatus.java", "+status")), List.of()
                ));

        var result = service.discover(request, DeliveryAssessmentSourceListener.NO_OP);

        assertThat(result.issues()).singleElement().satisfies(source ->
                assertThat(source.issue().doneAt()).isEqualTo(Instant.parse("2026-07-01T22:00:00Z"))
        );
        var searchRequest = ArgumentCaptor.forClass(JiraIssueSearchRequest.class);
        verify(searchPort).searchIssues(searchRequest.capture());
        assertThat(searchRequest.getValue().projectKey()).isEqualTo("CRM");
        assertThat(searchRequest.getValue().doneStatusId()).isEqualTo("Done");
        assertThat(searchRequest.getValue().toDateExclusive()).isEqualTo(LocalDate.parse("2026-07-03"));
        var materialRequest = ArgumentCaptor.forClass(JiraIssueMaterialRequest.class);
        verify(issuePort).getIssueMaterial(materialRequest.capture());
        assertThat(materialRequest.getValue().includeComments()).isFalse();
        assertThat(materialRequest.getValue().includeSubTasks()).isFalse();
        assertThat(materialRequest.getValue().includeParent()).isFalse();
        assertThat(materialRequest.getValue().customFieldIds()).isEmpty();
    }

    @Test
    void shouldFetchConfiguredTeamFieldAndExposeItOnDiscoveredIssue() {
        properties.setJiraDoneStatusId("10637");
        properties.setJiraTeamFieldId("customfield_10000");
        when(searchPort.searchIssues(any())).thenReturn(new JiraIssueSearchResult(
                "effective-jql", 1, false,
                List.of(new JiraIssueSearchItem("CRM-4", "Done", "done", null)),
                List.of()
        ));
        when(historyPort.getStatusHistory("CRM-4")).thenReturn(history(
                transition("2026-07-04T10:00:00Z", "done")
        ));
        when(issuePort.getIssueMaterial(any(JiraIssueMaterialRequest.class))).thenReturn(withCustomFields(
                material("CRM-4"),
                new JiraIssueCustomField("customfield_10000", "1900", "Team A", "Team A")
        ));
        when(gitLabPort.findMergeRequestsByIssueKey("crm/runtime", "CRM-4", 20))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-4", "crm/runtime", List.of(), List.of()));

        var result = service.discover(request("2026-07-04", "2026-07-04"),
                DeliveryAssessmentSourceListener.NO_OP);

        assertThat(result.issues()).singleElement().satisfies(source ->
                assertThat(source.issue().team()).satisfies(team -> {
                    assertThat(team.id()).isEqualTo("1900");
                    assertThat(team.name()).isEqualTo("Team A");
                    assertThat(team.fieldId()).isEqualTo("customfield_10000");
                })
        );
        var searchRequest = ArgumentCaptor.forClass(JiraIssueSearchRequest.class);
        verify(searchPort).searchIssues(searchRequest.capture());
        assertThat(searchRequest.getValue().doneStatusId()).isEqualTo("10637");
        var materialRequest = ArgumentCaptor.forClass(JiraIssueMaterialRequest.class);
        verify(issuePort).getIssueMaterial(materialRequest.capture());
        assertThat(materialRequest.getValue().customFieldIds()).containsExactly("customfield_10000");
        assertThat(result.issues().get(0).limitations())
                .noneMatch(limit -> limit.contains("Jira team field customfield_10000 was not available"));
    }

    @Test
    void shouldUseLatestDoneTransitionAfterReopen() {
        when(searchPort.searchIssues(any())).thenReturn(new JiraIssueSearchResult(
                "effective-jql", 1, false,
                List.of(new JiraIssueSearchItem("CRM-2", "Done", "done", null)), List.of()
        ));
        when(historyPort.getStatusHistory("CRM-2")).thenReturn(history(
                transition("2026-07-01T10:00:00Z", "done"),
                transition("2026-07-02T10:00:00Z", "indeterminate"),
                transition("2026-07-03T10:00:00Z", "done")
        ));
        when(issuePort.getIssueMaterial(any(JiraIssueMaterialRequest.class))).thenReturn(material("CRM-2"));
        when(gitLabPort.findMergeRequestsByIssueKey(any(), any(), anyInt()))
                .thenReturn(new GitLabMergeRequestSearchResult("CRM-2", "crm/runtime", List.of(), List.of()));

        var result = service.discover(request("2026-07-03", "2026-07-03"),
                DeliveryAssessmentSourceListener.NO_OP);

        assertThat(result.issues()).singleElement().satisfies(source ->
                assertThat(source.issue().doneAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"))
        );
        assertThat(result.issues().get(0).limitations())
                .contains("CRM-2: GitLab returned no merge request candidates for the configured group and issue-key search.");
    }

    @Test
    void shouldSkipIssueWhoseFinalDoneTransitionIsAtExclusiveEndBoundary() {
        when(searchPort.searchIssues(any())).thenReturn(new JiraIssueSearchResult(
                "effective-jql", 1, false,
                List.of(new JiraIssueSearchItem("CRM-3", "Done", "done", null)), List.of()
        ));
        when(historyPort.getStatusHistory("CRM-3")).thenReturn(history(
                transition("2026-07-02T22:00:00Z", "done")
        ));

        var result = service.discover(request("2026-07-02", "2026-07-02"),
                DeliveryAssessmentSourceListener.NO_OP);

        assertThat(result.issues()).isEmpty();
        assertThat(result.limitations()).anyMatch(limit -> limit.contains("CRM-3"));
        verify(issuePort, never()).getIssueMaterial(any(JiraIssueMaterialRequest.class));
        verify(gitLabPort, never()).findMergeRequestsByIssueKey(any(), any(), anyInt());
    }

    private DeliveryEffectivenessAssessmentJobStartRequest request(String from, String to) {
        return new DeliveryEffectivenessAssessmentJobStartRequest(
                "CRM", LocalDate.parse(from), LocalDate.parse(to), "gpt-5", "medium"
        );
    }

    private JiraIssueStatusHistory history(JiraIssueStatusTransition... transitions) {
        return new JiraIssueStatusHistory("CRM", false, List.of(transitions), List.of());
    }

    private JiraIssueStatusTransition transition(String timestamp, String category) {
        return new JiraIssueStatusTransition(
                Instant.parse(timestamp), "1", "Previous", "2", "Next", category
        );
    }

    private JiraIssueMaterial withCustomFields(JiraIssueMaterial material, JiraIssueCustomField... customFields) {
        return new JiraIssueMaterial(
                material.issueKey(),
                material.issueUrl(),
                material.summary(),
                material.description(),
                material.issueType(),
                material.status(),
                material.labels(),
                material.acceptanceCriteria(),
                material.links(),
                material.subTasks(),
                material.parentIssue(),
                material.confluencePages(),
                material.comments(),
                material.limitations(),
                List.of(customFields)
        );
    }
}
