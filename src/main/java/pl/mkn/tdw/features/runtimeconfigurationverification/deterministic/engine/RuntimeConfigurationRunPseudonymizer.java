package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class RuntimeConfigurationRunPseudonymizer {

    private final byte[] key;
    private final String runPrefix;
    private final Map<String, String> valueTokens = new LinkedHashMap<>();
    private final Map<String, String> keyTokens = new LinkedHashMap<>();

    RuntimeConfigurationRunPseudonymizer() {
        var random = new SecureRandom();
        key = new byte[32];
        random.nextBytes(key);
        var nonce = new byte[4];
        random.nextBytes(nonce);
        runPrefix = HexFormat.of().formatHex(nonce);
    }

    RuntimeConfigurationRunPseudonymizer(byte[] key, String runPrefix) {
        this.key = key.clone();
        this.runPrefix = runPrefix;
    }

    String valueToken(Object value) {
        var digest = digest("value", canonical(value));
        return valueTokens.computeIfAbsent(
                digest,
                ignored -> "value-" + runPrefix + "-" + String.format("%03d", valueTokens.size() + 1)
        );
    }

    String keyToken(String value) {
        var digest = digest("key", value != null ? value : "");
        return keyTokens.computeIfAbsent(
                digest,
                ignored -> "key-" + runPrefix + "-" + String.format("%03d", keyTokens.size() + 1)
        );
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationRunPseudonymizer[key=<redacted>, runPrefix=<redacted>, valueTokens="
                + valueTokens.size() + ", keyTokens=" + keyTokens.size() + "]";
    }

    private String digest(String namespace, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (namespace + "\u0000" + value).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }

    private String canonical(Object value) {
        return value == null
                ? "<null>"
                : value.getClass().getName() + ":" + value;
    }
}
