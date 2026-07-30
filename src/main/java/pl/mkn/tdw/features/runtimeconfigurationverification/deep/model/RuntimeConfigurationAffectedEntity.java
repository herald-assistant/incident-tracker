package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

import java.util.List;

public record RuntimeConfigurationAffectedEntity(
        String contextId,
        RuntimeConfigurationOperationalEntityType type,
        String entityId,
        String label,
        String summary,
        String evidenceKind,
        RuntimeConfigurationGroundingConfidence confidence,
        List<String> differenceIds,
        List<String> codeGroundingIds
) {

    public RuntimeConfigurationAffectedEntity {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        codeGroundingIds = codeGroundingIds != null ? List.copyOf(codeGroundingIds) : List.of();
    }
}
