package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubAppCopilotAccessTokenResolverTest {

    @Test
    void shouldRequirePrincipalForGithubAppMode() {
        var resolver = new GitHubAppCopilotAccessTokenResolver(principalId -> {
            throw new AssertionError("Token provider should not be called without a principal.");
        });

        assertThrows(
                GitHubCopilotAuthRequiredException.class,
                () -> resolver.resolve(new CopilotRunAuth(CopilotAuthMode.GITHUB_APP, null, null, true))
        );
    }

    @Test
    void shouldReturnUserBoundAccessTokenFromProvider() {
        var expiresAt = Instant.parse("2026-05-02T18:42:00Z");
        var resolver = new GitHubAppCopilotAccessTokenResolver(principalId -> new CopilotAccessToken(
                "ghu_user_token",
                "octocat",
                expiresAt,
                true
        ));

        var token = resolver.resolve(new CopilotRunAuth(
                CopilotAuthMode.GITHUB_APP,
                "operator-session-1",
                "octocat",
                true
        ));

        assertEquals("ghu_user_token", token.value());
        assertEquals("octocat", token.githubLogin());
        assertEquals(expiresAt, token.expiresAt());
        assertEquals(true, token.userBound());
    }

    @Test
    void shouldPropagateReauthRequiredFromProvider() {
        var resolver = new GitHubAppCopilotAccessTokenResolver(principalId -> {
            throw new GitHubCopilotReauthRequiredException();
        });

        assertThrows(
                GitHubCopilotReauthRequiredException.class,
                () -> resolver.resolve(new CopilotRunAuth(
                        CopilotAuthMode.GITHUB_APP,
                        "operator-session-1",
                        "octocat",
                        true
                ))
        );
    }
}
