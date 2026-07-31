package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;

import java.util.Objects;

public record RuntimeConfigurationWorkbenchConfigurationDiffResponse(
        String previewId,
        RuntimeConfigurationDiffProjection configurationDiff
) {

    public RuntimeConfigurationWorkbenchConfigurationDiffResponse {
        Objects.requireNonNull(configurationDiff, "configurationDiff is required");
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationWorkbenchConfigurationDiffResponse[previewId="
                + previewId
                + ", configurationDiff=<redacted>]";
    }
}
