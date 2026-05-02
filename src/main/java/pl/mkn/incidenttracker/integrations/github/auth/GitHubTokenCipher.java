package pl.mkn.incidenttracker.integrations.github.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GitHubTokenCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String PREFIX = "v1";

    private final GitHubAppAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return null;
        }

        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt GitHub token.", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }

        try {
            var parts = encryptedValue.split(":");
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted token format.");
            }
            var iv = Base64.getUrlDecoder().decode(parts[1]);
            var encrypted = Base64.getUrlDecoder().decode(parts[2]);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt GitHub token.", exception);
        }
    }

    private SecretKeySpec key() {
        var configuredKey = properties.getTokenEncryptionKey();
        if (!StringUtils.hasText(configuredKey)) {
            throw new IllegalStateException("analysis.github-app.token-encryption-key must be configured in GITHUB_APP mode.");
        }

        var keyBytes = decodeConfiguredKey(configuredKey.trim());
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] decodeConfiguredKey(String configuredKey) {
        try {
            var decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to deterministic derivation for local/test configuration strings.
        }

        return sha256(configuredKey);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive GitHub token encryption key.", exception);
        }
    }
}
