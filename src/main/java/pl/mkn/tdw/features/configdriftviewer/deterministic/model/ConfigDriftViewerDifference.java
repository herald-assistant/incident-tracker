package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

public record ConfigDriftViewerDifference(
        String differenceId,
        ConfigDriftViewerFileRole role,
        int documentIndex,
        String path,
        ConfigDriftViewerChangeKind kind,
        ConfigDriftViewerValueType sourceType,
        ConfigDriftViewerValueType targetType,
        ConfigDriftViewerSensitivity sensitivity,
        String sourceValueToken,
        String targetValueToken
) {
}
