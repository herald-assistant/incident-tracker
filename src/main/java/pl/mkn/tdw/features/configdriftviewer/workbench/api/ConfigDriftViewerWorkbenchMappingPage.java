package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.util.List;

public record ConfigDriftViewerWorkbenchMappingPage(
        String previewId,
        int offset,
        int limit,
        int totalItems,
        int totalNodes,
        boolean changedOnly,
        List<Item> items
) {

    public ConfigDriftViewerWorkbenchMappingPage {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public record Item(
            ConfigDriftViewerFileRole role,
            int documentIndex,
            int depth,
            String originalName,
            String originalPath,
            String sanitizedName,
            String sanitizedPath,
            ConfigDriftViewerValueType sourceType,
            ConfigDriftViewerValueType targetType,
            ConfigDriftViewerChangeKind changeKind,
            ConfigDriftViewerSensitivity sensitivity,
            String sourceValueToken,
            String targetValueToken,
            List<String> differenceIds
    ) {

        public Item {
            differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        }
    }
}
