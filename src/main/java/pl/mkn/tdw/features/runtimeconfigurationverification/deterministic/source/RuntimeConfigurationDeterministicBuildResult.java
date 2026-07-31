package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;

import java.util.Objects;

public record RuntimeConfigurationDeterministicBuildResult(
        RuntimeConfigurationDeterministicContext context,
        RuntimeConfigurationDiffProjection configurationDiff
) {

    public RuntimeConfigurationDeterministicBuildResult {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(configurationDiff, "configurationDiff is required");
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDeterministicBuildResult[context=<redacted>"
                + ", configurationDiff=<redacted>]";
    }
}
