package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabRepositoryBranchServiceTest {

    @Test
    void listsFilteredBranchesFromNestedProjectAndReportsMoreResults() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com/");
        properties.setToken("test-read-api-token");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var service = new GitLabRepositoryBranchService(
                properties, new GitLabRestClientFactory(properties, restClientBuilder));

        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/crm%2Fportal%2Ffrontend/repository/branches?per_page=100&search=release/2026"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "test-read-api-token"))
                .andRespond(withSuccess("""
                        [{"name":"release/2026.09","default":false},
                         {"name":"release/2026.10","default":true}]
                        """, MediaType.APPLICATION_JSON).header("X-Next-Page", "2"));

        var result = service.listBranches("crm/portal/frontend", "release/2026");

        assertEquals(2, result.branches().size());
        assertEquals("release/2026.09", result.branches().get(0).name());
        assertTrue(result.branches().get(1).isDefault());
        assertTrue(result.truncated());
        server.verify();
    }

    @Test
    void emptyBranchListIsNotTruncated() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var service = new GitLabRepositoryBranchService(
                properties, new GitLabRestClientFactory(properties, restClientBuilder));
        server.expect(requestTo("https://gitlab.example.com/api/v4/projects/crm%2Fbackend/repository/branches?per_page=100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var result = service.listBranches("crm/backend", "");

        assertTrue(result.branches().isEmpty());
        assertFalse(result.truncated());
        server.verify();
    }
}
