package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import java.util.Objects;

public record ConfigDriftViewerDiffDocument(
        int documentIndex,
        boolean sourcePresent,
        boolean targetPresent,
        ConfigDriftViewerDiffValue sourceProfile,
        ConfigDriftViewerDiffValue targetProfile,
        ConfigDriftViewerDiffNode root
) {

    public ConfigDriftViewerDiffDocument {
        if (documentIndex < 0) {
            throw new IllegalArgumentException("documentIndex cannot be negative");
        }
        Objects.requireNonNull(sourceProfile, "sourceProfile is required");
        Objects.requireNonNull(targetProfile, "targetProfile is required");
        Objects.requireNonNull(root, "root is required");
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDiffDocument[documentIndex=" + documentIndex
                + ", sourcePresent=" + sourcePresent
                + ", targetPresent=" + targetPresent
                + ", sourceProfile=<redacted>"
                + ", targetProfile=<redacted>"
                + ", root=<redacted>"
                + "]";
    }
}
