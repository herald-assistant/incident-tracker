package pl.mkn.tdw.features.configdriftviewer.deep.model;

import java.util.List;

public record ConfigDriftViewerAffectedEntity(
        String contextId,
        ConfigDriftViewerOperationalEntityType type,
        String entityId,
        String label,
        String summary,
        String evidenceKind,
        ConfigDriftViewerGroundingConfidence confidence,
        List<String> differenceIds,
        List<String> codeGroundingIds
) {

    public ConfigDriftViewerAffectedEntity {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        codeGroundingIds = codeGroundingIds != null ? List.copyOf(codeGroundingIds) : List.of();
    }
}
