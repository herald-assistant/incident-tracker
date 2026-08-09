package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

public record ConfigDriftViewerReference(
        String referenceId,
        ConfigDriftViewerFileRole sourceRole,
        int documentIndex,
        String sourcePath,
        String targetPath,
        String referenceKind,
        ConfigDriftViewerReferenceStatus sourceStatus,
        ConfigDriftViewerReferenceStatus targetStatus
) {
}
