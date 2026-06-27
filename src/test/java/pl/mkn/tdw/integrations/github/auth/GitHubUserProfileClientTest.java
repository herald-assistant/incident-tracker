package pl.mkn.tdw.integrations.github.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubUserProfileClientTest {

    @Test
    void shouldMapCurrentUserProfile() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GitHubUserProfileClient(restClientBuilder);

        server.expect(requestTo("https://api.github.com/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ghu_access"))
                .andRespond(withSuccess("""
                        {"id": 42, "login": "octocat"}
                        """, MediaType.APPLICATION_JSON));

        var profile = client.currentUser("ghu_access");

        assertEquals(42L, profile.id());
        assertEquals("octocat", profile.login());
        server.verify();
    }

    @Test
    void shouldMapProfileErrorsWithoutTokenLeak() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GitHubUserProfileClient(restClientBuilder);

        server.expect(requestTo("https://api.github.com/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withForbiddenRequest());

        var exception = assertThrows(
                GitHubOAuthExchangeException.class,
                () -> client.currentUser("ghu_secret_access")
        );

        assertEquals("GitHub user profile lookup failed with HTTP status 403.", exception.getMessage());
        assertFalse(exception.getMessage().contains("ghu_secret_access"));
        server.verify();
    }
}
