package pl.mkn.incidenttracker.integrations.github.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubAppOAuthClientTest {

    @Test
    void shouldExchangeCodeForUserAccessToken() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GitHubAppOAuthClient(properties(), restClientBuilder);

        server.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("code=code-123")))
                .andExpect(content().string(containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fauth%2Fgithub%2Fcallback")))
                .andExpect(content().string(containsString("code_verifier=verifier-123")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ghu_access",
                          "expires_in": 28800,
                          "refresh_token": "ghr_refresh",
                          "refresh_token_expires_in": 15768000,
                          "token_type": "bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.exchangeCode("code-123", "verifier-123");

        assertEquals("ghu_access", response.accessToken());
        assertEquals(28_800L, response.expiresIn());
        assertEquals("ghr_refresh", response.refreshToken());
        assertEquals(15_768_000L, response.refreshTokenExpiresIn());
        server.verify();
    }

    @Test
    void shouldRefreshUserAccessToken() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GitHubAppOAuthClient(properties(), restClientBuilder);

        server.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=ghr_old_refresh")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ghu_new_access",
                          "expires_in": 28800,
                          "refresh_token": "ghr_new_refresh",
                          "refresh_token_expires_in": 15768000,
                          "token_type": "bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.refresh("ghr_old_refresh");

        assertEquals("ghu_new_access", response.accessToken());
        assertEquals("ghr_new_refresh", response.refreshToken());
        server.verify();
    }

    @Test
    void shouldMapGithubErrorsToControlledExceptionWithoutRequestToken() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GitHubAppOAuthClient(properties(), restClientBuilder);

        server.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "bad_verification_code"}
                        """));

        var exception = assertThrows(
                GitHubOAuthExchangeException.class,
                () -> client.refresh("ghr_secret_refresh")
        );

        assertEquals("GitHub OAuth token exchange failed with HTTP status 400.", exception.getMessage());
        assertFalse(exception.getMessage().contains("ghr_secret_refresh"));
        server.verify();
    }

    private GitHubAppAuthProperties properties() {
        var properties = new GitHubAppAuthProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setCallbackUrl("http://localhost:8080/api/auth/github/callback");
        return properties;
    }
}
