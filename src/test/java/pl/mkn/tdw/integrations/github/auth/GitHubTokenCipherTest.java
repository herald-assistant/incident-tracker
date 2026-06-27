package pl.mkn.tdw.integrations.github.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubTokenCipherTest {

    @Test
    void shouldEncryptAndDecryptTokenWithoutPlaintextStorage() {
        var cipher = new GitHubTokenCipher(properties("test-encryption-key"));

        var encrypted = cipher.encrypt("ghu_secret_token");

        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("ghu_secret_token"));
        assertEquals("ghu_secret_token", cipher.decrypt(encrypted));
    }

    @Test
    void shouldRequireConfiguredEncryptionKey() {
        var cipher = new GitHubTokenCipher(properties(""));

        var exception = assertThrows(IllegalStateException.class, () -> cipher.encrypt("ghu_secret_token"));

        assertEquals(
                "analysis.github-app.token-encryption-key must be configured in GITHUB_APP mode.",
                exception.getMessage()
        );
        assertFalse(exception.getMessage().contains("ghu_secret_token"));
    }

    private GitHubAppAuthProperties properties(String key) {
        var properties = new GitHubAppAuthProperties();
        properties.setTokenEncryptionKey(key);
        return properties;
    }
}
