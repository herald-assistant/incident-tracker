package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;

import java.util.Objects;

public record ConfigDriftViewerDeterministicBuildResult(
        ConfigDriftViewerDeterministicContext context,
        ConfigDriftViewerDiffProjection configurationDiff
) {

    public ConfigDriftViewerDeterministicBuildResult {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(configurationDiff, "configurationDiff is required");
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDeterministicBuildResult[context=<redacted>"
                + ", configurationDiff=<redacted>]";
    }
}
