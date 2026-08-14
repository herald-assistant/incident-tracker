package pl.mkn.tdw.integrations.jira;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.confluence.ConfluencePageContent;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraRestIssueAdapterTest {

    @Test
    void shouldUseBoundedAssessmentProfileWithoutCommentsParentOrSubtasks() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, restClientBuilder),
                pageUrl -> Optional.empty()
        );

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(requestTo(containsString("customfield_10042")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .doesNotContain("comment", "parent", "subtasks"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-123",
                          "fields": {
                            "summary": "Customer status",
                            "description": "Expose status.",
                            "issuetype": { "name": "Story" },
                            "status": { "name": "Done" },
                            "labels": ["release"],
                            "customfield_10042": "Status is visible.",
                            "issuelinks": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-123/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial(JiraIssueMaterialRequest.assessment("CRM-123"));

        assertThat(material.comments()).isEmpty();
        assertThat(material.parentIssue()).isNull();
        assertThat(material.subTasks()).isEmpty();
        assertThat(material.acceptanceCriteria()).containsExactly("Status is visible.");
        server.verify();
    }

    @Test
    void shouldFetchJiraIssueMaterialWithRemoteLinksAndAcceptanceCriteria() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, restClientBuilder),
                pageUrl -> Optional.of(new ConfluencePageContent(
                        "123",
                        "Functional design",
                        pageUrl,
                        "Confluence describes CRM case profile data.",
                        "7",
                        List.of()
                ))
        );

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(requestTo(containsString("customfield_10042")))
                .andExpect(requestTo(containsString("subtasks")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer jira-token"))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-123",
                          "fields": {
                            "summary": "Customer status on profile",
                            "description": {
                              "content": [
                                {
                                  "content": [
                                    { "text": "Expose customer status in profile response." }
                                  ]
                                }
                              ]
                            },
                            "issuetype": { "name": "Story" },
                            "status": { "name": "Ready for Test" },
                            "labels": ["release", "smoke"],
                            "customfield_10042": "Status is visible for active customers.",
                            "subtasks": [
                              { "key": "CRM-124" }
                            ],
                            "issuelinks": [
                              {
                                "type": { "name": "relates to" },
                                "outwardIssue": {
                                  "key": "CRM-100",
                                  "fields": { "summary": "Parent business epic" }
                                }
                              }
                            ],
                            "comment": {
                              "comments": [
                                {
                                  "author": { "displayName": "Anna Kowalska" },
                                  "created": "2026-07-24T10:00:00.000+0000",
                                  "body": "Remember migrated customers."
                                }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-123/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "object": {
                              "title": "Functional design",
                              "url": "https://confluence.example.com/pages/123"
                            }
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-124?fields=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-124",
                          "fields": {
                            "summary": "Backend subtask",
                            "description": "Implement endpoint contract.",
                            "issuetype": { "name": "Sub-task" },
                            "status": { "name": "In Progress" },
                            "labels": [],
                            "customfield_10042": "Subtask acceptance criterion.",
                            "subtasks": [],
                            "issuelinks": [],
                            "comment": { "comments": [] }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-124/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial("CRM-123");

        assertThat(material.issueKey()).isEqualTo("CRM-123");
        assertThat(material.issueUrl()).isEqualTo("https://jira.example.com/browse/CRM-123");
        assertThat(material.summary()).isEqualTo("Customer status on profile");
        assertThat(material.description()).contains("Expose customer status");
        assertThat(material.acceptanceCriteria()).containsExactly("Status is visible for active customers.");
        assertThat(material.links()).extracting(JiraIssueLink::title)
                .contains("Parent business epic", "Functional design");
        assertThat(material.subTasks()).singleElement()
                .extracting(JiraIssueMaterial::issueKey)
                .isEqualTo("CRM-124");
        assertThat(material.confluencePages()).singleElement()
                .satisfies(page -> {
                    assertThat(page.pageId()).isEqualTo("123");
                    assertThat(page.content()).contains("CRM case profile data");
                });
        assertThat(material.comments()).singleElement()
                .extracting(JiraIssueComment::author)
                .isEqualTo("Anna Kowalska");

        server.verify();
    }

    @Test
    void shouldFetchParentContextWhenTargetIssueIsSubTask() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, restClientBuilder),
                pageUrl -> Optional.empty()
        );

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-124?fields=")))
                .andExpect(requestTo(containsString("parent")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-124",
                          "fields": {
                            "summary": "Backend subtask",
                            "description": "Implement endpoint contract.",
                            "issuetype": { "name": "Sub-task" },
                            "status": { "name": "In Progress" },
                            "labels": [],
                            "customfield_10042": "Subtask acceptance criterion.",
                            "parent": { "key": "CRM-123" },
                            "subtasks": [],
                            "issuelinks": [],
                            "comment": { "comments": [] }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-124/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-123",
                          "fields": {
                            "summary": "Parent story",
                            "description": "Collect CRM case profile data.",
                            "issuetype": { "name": "Story" },
                            "status": { "name": "In Progress" },
                            "labels": [],
                            "customfield_10042": "CRM case profile data is collected.",
                            "subtasks": [
                              { "key": "CRM-124" },
                              { "key": "CRM-125" }
                            ],
                            "issuelinks": [],
                            "comment": { "comments": [] }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-123/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-125?fields=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-125",
                          "fields": {
                            "summary": "Frontend subtask",
                            "description": "Expose CRM case controls",
                            "issuetype": { "name": "Sub-task" },
                            "status": { "name": "Open" },
                            "labels": [],
                            "customfield_10042": "Controls are visible.",
                            "subtasks": [],
                            "issuelinks": [],
                            "comment": { "comments": [] }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-125/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial("CRM-124");

        assertThat(material.issueKey()).isEqualTo("CRM-124");
        assertThat(material.parentIssue()).isNotNull();
        assertThat(material.parentIssue().issueKey()).isEqualTo("CRM-123");
        assertThat(material.parentIssue().acceptanceCriteria()).containsExactly("CRM case profile data is collected.");
        assertThat(material.parentIssue().subTasks()).singleElement()
                .extracting(JiraIssueMaterial::issueKey)
                .isEqualTo("CRM-125");

        server.verify();
    }

    private static JiraProperties jiraProperties() {
        var properties = new JiraProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setToken("jira-token");
        properties.setAcceptanceCriteriaFieldIds(List.of("customfield_10042"));
        return properties;
    }
}
