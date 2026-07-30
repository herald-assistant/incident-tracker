package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import java.util.List;

public record RuntimeConfigurationFinding(
        String findingId,
        String code,
        RuntimeConfigurationFindingSeverity severity,
        String path,
        List<String> differenceIds,
        List<String> referenceIds
) {

    public RuntimeConfigurationFinding {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        referenceIds = referenceIds != null ? List.copyOf(referenceIds) : List.of();
    }
}
