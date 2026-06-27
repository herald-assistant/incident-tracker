package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.Locale;

public enum GitLabEndpointUseCaseConfidence {
    HIGH,
    MEDIUM,
    LOW;

    public static GitLabEndpointUseCaseConfidence from(String value) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(value);
        if (normalized == null) {
            return LOW;
        }
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return LOW;
        }
    }
}
