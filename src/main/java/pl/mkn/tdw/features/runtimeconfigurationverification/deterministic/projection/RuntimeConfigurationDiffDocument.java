package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import java.util.Objects;

public record RuntimeConfigurationDiffDocument(
        int documentIndex,
        boolean sourcePresent,
        boolean targetPresent,
        RuntimeConfigurationDiffValue sourceProfile,
        RuntimeConfigurationDiffValue targetProfile,
        RuntimeConfigurationDiffNode root
) {

    public RuntimeConfigurationDiffDocument {
        if (documentIndex < 0) {
            throw new IllegalArgumentException("documentIndex cannot be negative");
        }
        Objects.requireNonNull(sourceProfile, "sourceProfile is required");
        Objects.requireNonNull(targetProfile, "targetProfile is required");
        Objects.requireNonNull(root, "root is required");
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDiffDocument[documentIndex=" + documentIndex
                + ", sourcePresent=" + sourcePresent
                + ", targetPresent=" + targetPresent
                + ", sourceProfile=<redacted>"
                + ", targetProfile=<redacted>"
                + ", root=<redacted>"
                + "]";
    }
}
