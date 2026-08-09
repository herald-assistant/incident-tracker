package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.util.List;

public record ConfigDriftViewerWorkbenchAnonymizationPage(
        String previewId,
        int offset,
        int limit,
        int totalItems,
        List<Item> items
) {

    public ConfigDriftViewerWorkbenchAnonymizationPage {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public record Item(
            ConfigDriftViewerFileRole role,
            int documentIndex,
            String path,
            ConfigDriftViewerChangeKind relation,
            ConfigDriftViewerSensitivity sensitivity,
            ConfigDriftViewerValueType sourceType,
            ConfigDriftViewerValueType targetType,
            ValueRepresentation sourceRepresentation,
            ValueRepresentation targetRepresentation,
            String sourceValueToken,
            String targetValueToken
    ) {
    }

    public enum ValueRepresentation {
        PSEUDONYMIZED,
        SUPPRESSED,
        STRUCTURE_ONLY,
        NOT_PRESENT
    }
}
