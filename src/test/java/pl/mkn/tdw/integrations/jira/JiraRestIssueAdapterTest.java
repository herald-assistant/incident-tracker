package pl.mkn.tdw.integrations.jira;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraRestIssueAdapterTest {

    @Test
    void shouldFetchJiraIssueMaterialWithRemoteLinksAndAcceptanceCriteria() {
        var properties = jiraProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new JiraRestIssueAdapter(properties, new JiraRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo(containsString("https://jira.example.com/rest/api/2/issue/CRM-123?fields=")))
                .andExpect(requestTo(containsString("customfield_10042")))
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

        var material = adapter.getIssueMaterial("CRM-123");

        assertThat(material.issueKey()).isEqualTo("CRM-123");
        assertThat(material.issueUrl()).isEqualTo("https://jira.example.com/browse/CRM-123");
        assertThat(material.summary()).isEqualTo("Customer status on profile");
        assertThat(material.description()).contains("Expose customer status");
        assertThat(material.acceptanceCriteria()).containsExactly("Status is visible for active customers.");
        assertThat(material.links()).extracting(JiraIssueLink::title)
                .contains("Parent business epic", "Functional design");
        assertThat(material.comments()).singleElement()
                .extracting(JiraIssueComment::author)
                .isEqualTo("Anna Kowalska");

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
