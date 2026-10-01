package pl.mkn.tdw.integrations.jira.adapter.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.confluence.contract.ConfluencePageContent;
import pl.mkn.tdw.integrations.jira.config.JiraProperties;
import pl.mkn.tdw.integrations.jira.config.JiraRestClientFactory;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueComment;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueLink;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterialRequest;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class JiraRestIssueAdapterTest {

    @Test
    void shouldUseAssessmentProfileWithHierarchyWithoutComments() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(restClientBuilder)),
                pageUrl -> Optional.empty()
        );

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(requestTo(containsString("customfield_10042")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .contains("timespent", "timeoriginalestimate", "timeestimate", "parent", "subtasks")
                        .doesNotContain("comment"))
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
                            "timespent": 14400,
                            "timeoriginalestimate": 28800,
                            "timeestimate": 7200,
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
        assertThat(material.timeTracking()).isNotNull().satisfies(timeTracking -> {
            assertThat(timeTracking.timeSpentSeconds()).isEqualTo(14400L);
            assertThat(timeTracking.originalEstimateSeconds()).isEqualTo(28800L);
            assertThat(timeTracking.remainingEstimateSeconds()).isEqualTo(7200L);
            assertThat(timeTracking.capturedAt()).isNotNull();
        });
        server.verify();
    }

    @Test
    void shouldFetchRequestedCustomFieldsForAssessmentMetadata() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(restClientBuilder)),
                pageUrl -> Optional.empty()
        );

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(requestTo(containsString("customfield_10000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "key": "CRM-123",
                          "fields": {
                            "summary": "Customer status",
                            "description": "Expose status.",
                            "issuetype": { "name": "Story" },
                            "status": { "name": "Done" },
                            "labels": [],
                            "customfield_10042": "Status is visible.",
                            "customfield_10000": { "id": "1900", "name": "Team A" },
                            "issuelinks": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://jira.example.com/rest/api/2/issue/CRM-123/remotelink"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial(JiraIssueMaterialRequest.assessment(
                "CRM-123",
                List.of("customfield_10000")
        ));

        assertThat(material.customFields()).singleElement().satisfies(field -> {
            assertThat(field.fieldId()).isEqualTo("customfield_10000");
            assertThat(field.id()).isEqualTo("1900");
            assertThat(field.name()).isEqualTo("Team A");
            assertThat(field.value()).isEqualTo("Team A");
        });
        assertThat(material.timeTracking()).isNotNull().satisfies(timeTracking -> {
            assertThat(timeTracking.timeSpentSeconds()).isNull();
            assertThat(timeTracking.originalEstimateSeconds()).isNull();
            assertThat(timeTracking.remainingEstimateSeconds()).isNull();
            assertThat(timeTracking.capturedAt()).isNotNull();
        });
        server.verify();
    }

    @Test
    void shouldFetchJiraIssueMaterialWithRemoteLinksAndAcceptanceCriteria() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(restClientBuilder)),
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
                                  "author": { "displayName": "jira-comment-author-1" },
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
                .isEqualTo("jira-comment-author-1");

        server.verify();
    }

    @Test
    void shouldFetchParentContextWhenTargetIssueIsSubTask() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(
                properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(restClientBuilder)),
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

    @ParameterizedTest
    @CsvSource({"Story,Sub-task", "Dev Story,Sub Task dev", "CRM Delivery,CRM Implementation"})
    void shouldFetchOnlyDirectHierarchyWithoutCommentsRegardlessOfTypeNames(String parentType, String childType) {
        var properties = jiraProperties();
        var client = RestClient.builder();
        var server = MockRestServiceServer.bindTo(client).build();
        var adapter = new JiraRestIssueAdapter(properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(client)),
                pageUrl -> Optional.empty());

        server.expect(requestTo(containsString("/issue/CRM-124?fields=")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .contains("parent", "subtasks").doesNotContain("comment"))
                .andRespond(withSuccess("""
                        {"key":"CRM-124","fields":{
                          "summary":"Customer status endpoint","issuetype":{"name":"%s","subtask":true},
                          "parent":{"key":"CRM-123"},"subtasks":[{"key":"CRM-126"},{"key":"CRM-126"}],
                          "comment":{"comments":[{"body":"private target comment"}]}
                        }}
                        """.formatted(childType), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-124/remotelink")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-123?fields=")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .doesNotContain("comment", "parent", "subtasks"))
                .andRespond(withSuccess("""
                        {"key":"CRM-123","fields":{
                          "summary":"Customer status delivery","issuetype":{"name":"%s","subtask":false},
                          "parent":{"key":"CRM-100"},"subtasks":[{"key":"CRM-125"}],
                          "comment":{"comments":[{"body":"private parent comment"}]}
                        }}
                        """.formatted(parentType), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-123/remotelink")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-126?fields=")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .doesNotContain("comment", "parent", "subtasks"))
                .andRespond(withSuccess("""
                        {"key":"CRM-126","fields":{
                          "summary":"Customer status validation","issuetype":{"name":"%s"},
                          "parent":{"key":"CRM-124"},"subtasks":[{"key":"CRM-127"}],
                          "comment":{"comments":[{"body":"private child comment"}]}
                        }}
                        """.formatted(childType), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-126/remotelink")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial(JiraIssueMaterialRequest.assessment("CRM-124"));

        assertThat(material.comments()).isEmpty();
        assertThat(material.parentIssue()).satisfies(parent -> {
            assertThat(parent.issueKey()).isEqualTo("CRM-123");
            assertThat(parent.comments()).isEmpty();
            assertThat(parent.parentIssue()).isNull();
            assertThat(parent.subTasks()).isEmpty();
        });
        assertThat(material.subTasks()).singleElement().satisfies(child -> {
            assertThat(child.issueKey()).isEqualTo("CRM-126");
            assertThat(child.comments()).isEmpty();
            assertThat(child.parentIssue()).isNull();
            assertThat(child.subTasks()).isEmpty();
        });
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"CRM Implementation,true", "Sub Task dev,false"})
    void shouldUseJiraSubtaskFlagOnlyToReportMissingParent(String typeName, boolean subtask) {
        var properties = jiraProperties();
        var client = RestClient.builder();
        var server = MockRestServiceServer.bindTo(client).build();
        var adapter = new JiraRestIssueAdapter(properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(client)),
                pageUrl -> Optional.empty());
        server.expect(requestTo(containsString("/issue/CRM-124?fields=")))
                .andRespond(withSuccess("""
                        {"key":"CRM-124","fields":{"issuetype":{"name":"%s","subtask":%s}}}
                        """.formatted(typeName, subtask), MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial(new JiraIssueMaterialRequest(
                "CRM-124", false, false, false, true, true, false));

        assertThat(material.parentIssue()).isNull();
        assertThat(material.limitations().stream().anyMatch(limit -> limit.contains("parent key")))
                .isEqualTo(subtask);
        server.verify();
    }

    @Test
    void shouldReadExplicitParentWithoutSubtaskFlagOrMatchingTypeName() {
        var properties = jiraProperties();
        var client = RestClient.builder();
        var server = MockRestServiceServer.bindTo(client).build();
        var adapter = new JiraRestIssueAdapter(properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(client)),
                pageUrl -> Optional.empty());
        server.expect(requestTo(containsString("/issue/CRM-124?fields=")))
                .andRespond(withSuccess("""
                        {"key":"CRM-124","fields":{"issuetype":{"name":"CRM Implementation","subtask":false},
                          "parent":{"key":"CRM-123"}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-123?fields=")))
                .andRespond(withSuccess("""
                        {"key":"CRM-123","fields":{"summary":"Customer status delivery"}}
                        """, MediaType.APPLICATION_JSON));

        var material = adapter.getIssueMaterial(new JiraIssueMaterialRequest(
                "CRM-124", false, false, false, true, true, false));

        assertThat(material.parentIssue().issueKey()).isEqualTo("CRM-123");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void shouldKeepTargetAndBoundFailedChildrenWhileReportingMissingParent(int parentStatus) {
        var properties = jiraProperties();
        properties.setMaxSubTasks(1);
        var client = RestClient.builder();
        var server = MockRestServiceServer.bindTo(client).build();
        var adapter = new JiraRestIssueAdapter(properties,
                new JiraRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(client)),
                pageUrl -> Optional.empty());
        server.expect(requestTo(containsString("/issue/CRM-200?fields=")))
                .andRespond(withSuccess("""
                        {"key":"CRM-200","fields":{"summary":"Customer status delivery",
                          "parent":{"key":"CRM-100"},"subtasks":[{"key":"CRM-201"},{"key":"CRM-202"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/issue/CRM-100?fields=")))
                .andRespond(withStatus(HttpStatus.valueOf(parentStatus)));
        server.expect(requestTo(containsString("/issue/CRM-201?fields=")))
                .andRespond(withServerError());

        var material = adapter.getIssueMaterial(new JiraIssueMaterialRequest(
                "CRM-200", false, false, false, true, true, false));

        assertThat(material.summary()).isEqualTo("Customer status delivery");
        assertThat(material.parentIssue().issueKey()).isEqualTo("CRM-100");
        assertThat(material.parentIssue().status()).isEqualTo(parentStatus == 404 ? "NOT_FOUND" : "UNAVAILABLE");
        assertThat(material.subTasks()).singleElement().satisfies(child -> {
            assertThat(child.issueKey()).isEqualTo("CRM-201");
            assertThat(child.status()).isEqualTo("UNAVAILABLE");
        });
        assertThat(material.limitations())
                .anyMatch(limit -> limit.contains("CRM-100")
                        && (limit.contains("not visible") || limit.contains("could not be fetched")))
                .anyMatch(limit -> limit.contains("CRM-201") && limit.contains("could not be fetched"))
                .anyMatch(limit -> limit.contains("max-sub-tasks=1"));
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
