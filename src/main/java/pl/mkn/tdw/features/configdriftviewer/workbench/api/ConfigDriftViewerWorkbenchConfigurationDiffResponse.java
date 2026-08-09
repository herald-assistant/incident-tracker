package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;

import java.util.Objects;

public record ConfigDriftViewerWorkbenchConfigurationDiffResponse(
        String previewId,
        ConfigDriftViewerDiffProjection configurationDiff
) {

    public ConfigDriftViewerWorkbenchConfigurationDiffResponse {
        Objects.requireNonNull(configurationDiff, "configurationDiff is required");
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerWorkbenchConfigurationDiffResponse[previewId="
                + previewId
                + ", configurationDiff=<redacted>]";
    }
}
