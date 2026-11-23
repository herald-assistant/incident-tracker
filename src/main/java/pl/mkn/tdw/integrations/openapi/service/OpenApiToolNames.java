package pl.mkn.tdw.integrations.openapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Names are independent of selection order and shared by every execution channel. */
final class OpenApiToolNames {
    private static final int MAX_LENGTH = 64;
    private static final int HASH_LENGTH = 16;

    private OpenApiToolNames() {}

    static boolean validOperationId(String operationId) {
        return operationId.matches("[a-zA-Z][a-zA-Z0-9_]*");
    }

    static String name(String apiId, String operationId) {
        var full = "oa_" + apiId + "_" + operationId;
        if (full.length() <= MAX_LENGTH) return full;
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(full.getBytes(StandardCharsets.UTF_8));
            var suffix = HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
            return full.substring(0, MAX_LENGTH - HASH_LENGTH - 1) + "_" + suffix;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 nie jest dostępny.", ex);
        }
    }
}
