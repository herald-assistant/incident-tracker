package pl.mkn.incidenttracker.api.githubauth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotAuthMode;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubAppAuthProperties;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubAppAuthorizationStore;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubAppOAuthClient;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubAppTokenResponse;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthExchangeException;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthState;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthStateInvalidException;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthStateStore;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubTokenCipher;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubUserProfile;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubUserProfileClient;
import pl.mkn.incidenttracker.integrations.github.auth.InMemoryGitHubAppAuthorizationStore;
import pl.mkn.incidenttracker.integrations.github.auth.InMemoryGitHubOAuthStateStore;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubAuthServiceTest {

    @Test
    void shouldReturnLocalTokenStatusWithoutOperatorCookie() {
        var fixture = fixture(CopilotAuthMode.LOCAL_TOKEN);
        fixture.copilotProperties.getAuth().getLocal().setDisplayName("Local test token");
        var response = new MockHttpServletResponse();

        var status = fixture.service.status(new MockHttpServletRequest(), response);

        assertEquals("LOCAL_TOKEN", status.mode());
        assertFalse(status.required());
        assertTrue(status.connected());
        assertEquals("Local test token", status.displayName());
        assertEquals(null, response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldCreateOperatorSessionCookieForDisconnectedGithubAppStatus() {
        var fixture = fixture(CopilotAuthMode.GITHUB_APP);
        var response = new MockHttpServletResponse();

        var status = fixture.service.status(new MockHttpServletRequest(), response);

        assertEquals("GITHUB_APP", status.mode());
        assertTrue(status.required());
        assertFalse(status.connected());
        assertTrue(status.reauthRequired());
        assertEquals("/api/auth/github/start", status.authStartUrl());
        var cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("incident_tracker_operator="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=lax"));
        assertFalse(cookie.contains("ghu_"));
    }

    @Test
    void shouldRejectExternalReturnUrlBeforeOAuthRedirect() {
        var fixture = fixture(CopilotAuthMode.GITHUB_APP);

        assertThrows(
                GitHubOAuthExchangeException.class,
                () -> fixture.service.start("https://evil.example/callback", new MockHttpServletRequest(), new MockHttpServletResponse())
        );
    }

    @Test
    void shouldCreateStateAndRedirectToGithubWithPkce() {
        var fixture = fixture(CopilotAuthMode.GITHUB_APP);
        var response = new MockHttpServletResponse();

        var redirect = fixture.service.start("/elastic?analysisId=123", new MockHttpServletRequest(), response);
        var query = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();

        assertEquals("https", redirect.getScheme());
        assertEquals("github.com", redirect.getHost());
        assertEquals("/login/oauth/authorize", redirect.getPath());
        assertEquals("client-id", query.getFirst("client_id"));
        assertEquals("http://localhost:8080/api/auth/github/callback", query.getFirst("redirect_uri"));
        assertNotNull(query.getFirst("state"));
        assertNotNull(query.getFirst("code_challenge"));
        assertEquals("S256", query.getFirst("code_challenge_method"));
        assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldRejectCallbackWithInvalidState() {
        var fixture = fixture(CopilotAuthMode.GITHUB_APP);
        var request = requestWithOperatorSession("operator-session-1");

        assertThrows(
                GitHubOAuthStateInvalidException.class,
                () -> fixture.service.callback("code", "missing-state", null, request)
        );
    }

    @Test
    void shouldExchangeCallbackCodeAndStoreEncryptedAuthorization() {
        var fixture = fixture(CopilotAuthMode.GITHUB_APP);
        var request = requestWithOperatorSession("operator-session-1");
        fixture.stateStore.save(new GitHubOAuthState(
                "state-123",
                "operator-session-1",
                "/?from=test",
                Instant.parse("2026-05-02T10:00:00Z"),
                Instant.parse("2099-05-02T10:10:00Z"),
                "verifier-123"
        ));
        when(fixture.oauthClient.exchangeCode(eq("code-123"), eq("verifier-123")))
                .thenReturn(new GitHubAppTokenResponse(
                        "ghu_access_token",
                        28_800L,
                        "ghr_refresh_token",
                        15_768_000L,
                        "bearer",
                        "",
                        null,
                        null
                ));
        when(fixture.userProfileClient.currentUser("ghu_access_token"))
                .thenReturn(new GitHubUserProfile(42L, "octocat"));

        var redirect = fixture.service.callback("code-123", "state-123", null, request);

        assertEquals("/?from=test&githubAuth=connected", redirect.toString());
        var stored = fixture.authorizationStore.findActiveByOperatorSessionId("operator-session-1").orElseThrow();
        assertEquals(42L, stored.githubUserId());
        assertEquals("octocat", stored.githubLogin());
        assertFalse(stored.encryptedAccessToken().contains("ghu_access_token"));
        assertFalse(stored.encryptedRefreshToken().contains("ghr_refresh_token"));
        assertEquals("ghu_access_token", fixture.tokenCipher.decrypt(stored.encryptedAccessToken()));
        verify(fixture.userProfileClient).verifyRequiredOrg("ghu_access_token", "");
    }

    private MockHttpServletRequest requestWithOperatorSession(String sessionId) {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("incident_tracker_operator", sessionId));
        return request;
    }

    private AuthFixture fixture(CopilotAuthMode mode) {
        var copilotProperties = new CopilotSdkProperties();
        copilotProperties.getAuth().setMode(mode);
        var githubProperties = new GitHubAppAuthProperties();
        githubProperties.setClientId("client-id");
        githubProperties.setClientSecret("client-secret");
        githubProperties.setCallbackUrl("http://localhost:8080/api/auth/github/callback");
        githubProperties.setTokenEncryptionKey("test-encryption-key");
        githubProperties.setRequiredOrg("");
        var stateStore = new InMemoryGitHubOAuthStateStore();
        var authorizationStore = new InMemoryGitHubAppAuthorizationStore();
        var tokenCipher = new GitHubTokenCipher(githubProperties);
        var oauthClient = mock(GitHubAppOAuthClient.class);
        var userProfileClient = mock(GitHubUserProfileClient.class);
        var service = new GitHubAuthService(
                copilotProperties,
                githubProperties,
                new OperatorSessionService(githubProperties),
                stateStore,
                oauthClient,
                userProfileClient,
                authorizationStore,
                tokenCipher
        );
        return new AuthFixture(
                copilotProperties,
                stateStore,
                authorizationStore,
                tokenCipher,
                oauthClient,
                userProfileClient,
                service
        );
    }

    private record AuthFixture(
            CopilotSdkProperties copilotProperties,
            GitHubOAuthStateStore stateStore,
            GitHubAppAuthorizationStore authorizationStore,
            GitHubTokenCipher tokenCipher,
            GitHubAppOAuthClient oauthClient,
            GitHubUserProfileClient userProfileClient,
            GitHubAuthService service
    ) {
    }
}
