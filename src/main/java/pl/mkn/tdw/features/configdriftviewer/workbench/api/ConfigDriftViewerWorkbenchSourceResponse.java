package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerBranchCoverage;

public record ConfigDriftViewerWorkbenchSourceResponse(
        String previewId,
        String configurationDirectory,
        ConfigDriftViewerBranchCoverage source,
        ConfigDriftViewerBranchCoverage target
) {
}
