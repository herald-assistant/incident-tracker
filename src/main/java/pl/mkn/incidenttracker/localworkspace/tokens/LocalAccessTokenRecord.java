package pl.mkn.incidenttracker.localworkspace.tokens;

import java.time.Instant;

public record LocalAccessTokenRecord(
        String tokenRef,
        String accessToken,
        String label,
        Instant updatedAt
) {
    public LocalAccessTokenRecord {
        tokenRef = required("tokenRef", tokenRef);
        accessToken = required("accessToken", accessToken);
        label = label == null || label.isBlank() ? tokenRef : label.trim();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    private static String required(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
