package pl.mkn.tdw.integrations.gitlab.contract;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Metadata for a listed ref, separate from its identity used in requests. */
public record GitLabRefOption(GitLabRef ref, String committedAt, boolean isDefault) {
    public GitLabRefOption {
        Objects.requireNonNull(ref, "Ref is required");
        if (committedAt != null) {
            try {
                committedAt = Instant.parse(committedAt).toString();
            } catch (DateTimeParseException exception) {
                committedAt = null;
            }
        }
    }
}
