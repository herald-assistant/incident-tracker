package pl.mkn.tdw.features.configdriftviewer.workbench;

import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchAnonymizationPage;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchMappingPage;

import java.util.List;

record ConfigDriftViewerWorkbenchPreviewSnapshot(
        ConfigDriftViewerMode mode,
        ConfigDriftViewerDeterministicContext deterministic,
        ConfigDriftViewerDiffProjection configurationDiff,
        ConfigDriftViewerDeepContext deepContext,
        ConfigDriftViewerPromptPreparation preparation,
        List<ConfigDriftViewerWorkbenchMappingPage.Item> mappingItems,
        List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> anonymizationItems
) {

    ConfigDriftViewerWorkbenchPreviewSnapshot {
        mappingItems = mappingItems != null ? List.copyOf(mappingItems) : List.of();
        anonymizationItems = anonymizationItems != null
                ? List.copyOf(anonymizationItems)
                : List.of();
    }
}
