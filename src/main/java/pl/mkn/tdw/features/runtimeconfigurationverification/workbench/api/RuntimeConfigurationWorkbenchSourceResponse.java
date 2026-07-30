package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;

public record RuntimeConfigurationWorkbenchSourceResponse(
        String previewId,
        String configurationDirectory,
        RuntimeConfigurationBranchCoverage source,
        RuntimeConfigurationBranchCoverage target
) {
}
