package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalCopilotAccessTokenResolverTest {

    @Test
    void shouldReturnConfiguredLocalToken() {
        var properties = new CopilotSdkProperties();
        properties.getAuth().getLocal().setGithubToken("  ghu_local_token  ");
        var resolver = new LocalCopilotAccessTokenResolver(properties);

        var token = resolver.resolve(CopilotRunAuth.localToken());

        assertEquals("ghu_local_token", token.value());
        assertFalse(token.userBound());
    }

    @Test
    void shouldUseLegacyGithubTokenAsFallback() {
        var properties = new CopilotSdkProperties();
        properties.setGithubToken("ghp_legacy_token");
        var resolver = new LocalCopilotAccessTokenResolver(properties);

        var token = resolver.resolve(CopilotRunAuth.localToken());

        assertEquals("ghp_legacy_token", token.value());
    }

    @Test
    void shouldFailClearlyWhenLocalTokenIsMissingWithoutExposingSecrets() {
        var resolver = new LocalCopilotAccessTokenResolver(new CopilotSdkProperties());

        var exception = assertThrows(
                CopilotLocalTokenMissingException.class,
                () -> resolver.resolve(CopilotRunAuth.localToken())
        );

        assertEquals(
                "Tryb LOCAL_TOKEN wymaga skonfigurowania analysis.ai.copilot.auth.local.github-token albo COPILOT_GITHUB_TOKEN.",
                exception.getMessage()
        );
        assertFalse(exception.getMessage().contains("ghu_"));
        assertFalse(exception.getMessage().contains("ghp_"));
    }
}
