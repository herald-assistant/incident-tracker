package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import java.util.List;

public record RuntimeConfigurationFinding(
        String findingId,
        String code,
        RuntimeConfigurationFindingSeverity severity,
        String path,
        List<String> differenceIds,
        List<String> referenceIds,
        String filePath,
        Integer line
) {

    public RuntimeConfigurationFinding(
            String findingId,
            String code,
            RuntimeConfigurationFindingSeverity severity,
            String path,
            List<String> differenceIds,
            List<String> referenceIds
    ) {
        this(findingId, code, severity, path, differenceIds, referenceIds, null, null);
    }

    public RuntimeConfigurationFinding {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        referenceIds = referenceIds != null ? List.copyOf(referenceIds) : List.of();
    }
}
