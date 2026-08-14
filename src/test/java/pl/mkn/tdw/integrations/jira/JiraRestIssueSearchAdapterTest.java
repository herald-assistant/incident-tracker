package pl.mkn.tdw.integrations.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraRestIssueSearchAdapterTest {

    @Test
    void shouldMapTypedCriteriaToJqlAndPageSearchResults() {
        var fixture = fixture();
        var jql = "project = \"CRM\" AND statusCategory = Done"
                + " AND statusCategoryChangedDate >= \"2026-07-01\""
                + " AND statusCategoryChangedDate < \"2026-08-01\" ORDER BY key ASC";
        fixture.server.expect(requestTo("https://jira.example.com/rest/api/2/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jira-token"))
                .andExpect(request -> assertSearchBody(request, jql, 0, 2))
                .andRespond(withSuccess("""
                        {
                          "total": 3,
                          "issues": [
                            {"key":"CRM-1","fields":{"status":{"name":"Done","statusCategory":{"key":"done"}},"statuscategorychangedate":"2026-07-10T10:00:00Z"}},
                            {"key":"CRM-2","fields":{"status":{"name":"Closed","statusCategory":{"key":"done"}},"statuscategorychangedate":"2026-07-11T10:00:00Z"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://jira.example.com/rest/api/2/search"))
                .andExpect(request -> assertSearchBody(request, jql, 2, 2))
                .andRespond(withSuccess("""
                        {
                          "total": 3,
                          "issues": [
                            {"key":"CRM-3","fields":{"status":{"name":"Done","statusCategory":{"key":"done"}},"statuscategorychangedate":"2026-07-12T10:00:00Z"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = fixture.adapter.searchIssues(new JiraIssueSearchRequest(
                "crm", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"), 2, 10
        ));

        assertThat(result.effectiveJql()).isEqualTo(jql);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.truncated()).isFalse();
        assertThat(result.issues()).extracting(JiraIssueSearchItem::issueKey)
                .containsExactly("CRM-1", "CRM-2", "CRM-3");
        assertThat(result.issues().get(0).statusCategoryChangedAt())
                .isEqualTo(Instant.parse("2026-07-10T10:00:00Z"));
        fixture.server.verify();
    }

    @Test
    void shouldReportSearchTruncationAtConfiguredIssueLimit() {
        var fixture = fixture();
        fixture.server.expect(requestTo("https://jira.example.com/rest/api/2/search"))
                .andRespond(withSuccess("""
                        {"total":5,"issues":[
                          {"key":"CRM-1","fields":{"status":{"statusCategory":{"key":"done"}}}},
                          {"key":"CRM-2","fields":{"status":{"statusCategory":{"key":"done"}}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var result = fixture.adapter.searchIssues(new JiraIssueSearchRequest(
                "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"), 50, 2
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.limitations()).singleElement().asString().contains("truncated to 2 issues");
        fixture.server.verify();
    }

    @Test
    void shouldResolveStatusIdsToCategoriesForChangelogTransitions() {
        var fixture = fixture();
        fixture.server.expect(requestTo(containsString(
                        "https://jira.example.com/rest/api/2/issue/CRM-1?fields=status&expand=changelog")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "changelog": {
                            "total": 2,
                            "histories": [
                              {"created":"2026-07-10T10:00:00.000+0000","items":[{"field":"status","from":"1","fromString":"Open","to":"2","toString":"Done"}]},
                              {"created":"2026-07-11T10:00:00.000+0000","items":[{"field":"labels","from":null,"to":null}]}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://jira.example.com/rest/api/2/status/2"))
                .andRespond(withSuccess("{" + "\"statusCategory\":{\"key\":\"done\"}}", MediaType.APPLICATION_JSON));

        var history = fixture.adapter.getStatusHistory("CRM-1");

        assertThat(history.truncated()).isFalse();
        assertThat(history.transitions()).singleElement().satisfies(transition -> {
            assertThat(transition.changedAt()).isEqualTo(Instant.parse("2026-07-10T10:00:00Z"));
            assertThat(transition.toStatusCategory()).isEqualTo("done");
        });
        fixture.server.verify();
    }

    private static Fixture fixture() {
        var properties = new JiraProperties();
        properties.setBaseUrl("https://jira.example.com");
        properties.setToken("jira-token");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                new JiraRestIssueSearchAdapter(properties, new JiraRestClientFactory(properties, builder)),
                server
        );
    }

    private static void assertSearchBody(
            ClientHttpRequest request,
            String jql,
            int startAt,
            int maxResults
    ) throws IOException {
        var json = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsBytes());
        assertThat(json.path("jql").asText()).isEqualTo(jql);
        assertThat(json.path("startAt").asInt()).isEqualTo(startAt);
        assertThat(json.path("maxResults").asInt()).isEqualTo(maxResults);
        assertThat(json.path("fields")).extracting(node -> node.asText())
                .containsExactly("status", "statuscategorychangedate");
    }

    private record Fixture(JiraRestIssueSearchAdapter adapter, MockRestServiceServer server) {
    }
}
