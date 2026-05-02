package pl.mkn.incidenttracker.integrations.github.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GitHubAppTokenRefreshServiceTest {

    @Test
    void shouldMarkTokenAsUsedWhenItIsStillFresh() {
        var fixture = fixture();
        var now = Instant.parse("2026-05-02T10:00:00Z");
        var authorization = fixture.saveAuthorization(
                "ghu_access",
                now.plus(Duration.ofHours(2)),
                "ghr_refresh",
                now.plus(Duration.ofDays(30))
        );

        var refreshed = fixture.service.ensureFresh(authorization, now);

        assertEquals("ghu_access", fixture.cipher.decrypt(refreshed.encryptedAccessToken()));
        assertEquals(now, refreshed.lastUsedAt());
        verifyNoInteractions(fixture.oauthClient);
    }

    @Test
    void shouldRefreshTokenNearExpirationAndRotateStoredTokensAtomically() {
        var fixture = fixture();
        var now = Instant.parse("2026-05-02T10:00:00Z");
        var authorization = fixture.saveAuthorization(
                "ghu_old_access",
                now.plus(Duration.ofMinutes(1)),
                "ghr_old_refresh",
                now.plus(Duration.ofDays(30))
        );
        when(fixture.oauthClient.refresh("ghr_old_refresh")).thenReturn(new GitHubAppTokenResponse(
                "ghu_new_access",
                28_800L,
                "ghr_new_refresh",
                15_768_000L,
                "bearer",
                "",
                null,
                null
        ));

        var refreshed = fixture.service.ensureFresh(authorization, now);

        assertEquals("ghu_new_access", fixture.cipher.decrypt(refreshed.encryptedAccessToken()));
        assertEquals("ghr_new_refresh", fixture.cipher.decrypt(refreshed.encryptedRefreshToken()));
        assertEquals(now.plusSeconds(28_800L), refreshed.accessTokenExpiresAt());
        assertEquals(now.plusSeconds(15_768_000L), refreshed.refreshTokenExpiresAt());
        assertEquals(now, refreshed.updatedAt());
        assertFalse(refreshed.toString().contains("ghu_new_access"));
        assertFalse(refreshed.toString().contains("ghr_new_refresh"));
        verify(fixture.oauthClient).refresh("ghr_old_refresh");
    }

    @Test
    void shouldRequireReauthWhenRefreshTokenIsMissing() {
        var fixture = fixture();
        var now = Instant.parse("2026-05-02T10:00:00Z");
        var authorization = fixture.saveAuthorization(
                "ghu_old_access",
                now.minus(Duration.ofMinutes(1)),
                null,
                null
        );

        assertThrows(
                GitHubAppAuthorizationReauthRequiredException.class,
                () -> fixture.service.ensureFresh(authorization, now)
        );
        verifyNoInteractions(fixture.oauthClient);
    }

    private RefreshFixture fixture() {
        var properties = new GitHubAppAuthProperties();
        properties.setTokenEncryptionKey("test-encryption-key");
        properties.setTokenRefreshSkew(Duration.ofMinutes(5));
        var cipher = new GitHubTokenCipher(properties);
        var store = new InMemoryGitHubAppAuthorizationStore();
        var oauthClient = mock(GitHubAppOAuthClient.class);
        return new RefreshFixture(
                cipher,
                store,
                oauthClient,
                new GitHubAppTokenRefreshService(properties, oauthClient, store, cipher)
        );
    }

    private record RefreshFixture(
            GitHubTokenCipher cipher,
            InMemoryGitHubAppAuthorizationStore store,
            GitHubAppOAuthClient oauthClient,
            GitHubAppTokenRefreshService service
    ) {

        private GitHubAppAuthorization saveAuthorization(
                String accessToken,
                Instant accessTokenExpiresAt,
                String refreshToken,
                Instant refreshTokenExpiresAt
        ) {
            var now = Instant.parse("2026-05-02T09:00:00Z");
            return store.save(new GitHubAppAuthorization(
                    "operator-session-1",
                    42L,
                    "octocat",
                    cipher.encrypt(accessToken),
                    accessTokenExpiresAt,
                    cipher.encrypt(refreshToken),
                    refreshTokenExpiresAt,
                    now,
                    now,
                    now,
                    null
            ));
        }
    }
}
