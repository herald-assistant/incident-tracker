package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;

public record ConfigDriftViewerWorkbenchDeepResponse(
        String previewId,
        boolean requested,
        ConfigDriftViewerDeepContext context
) {
}
