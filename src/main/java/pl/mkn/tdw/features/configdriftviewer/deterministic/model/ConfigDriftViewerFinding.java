package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import java.util.List;

public record ConfigDriftViewerFinding(
        String findingId,
        String code,
        ConfigDriftViewerFindingSeverity severity,
        String path,
        List<String> differenceIds,
        List<String> referenceIds,
        String filePath,
        Integer line
) {

    public ConfigDriftViewerFinding(
            String findingId,
            String code,
            ConfigDriftViewerFindingSeverity severity,
            String path,
            List<String> differenceIds,
            List<String> referenceIds
    ) {
        this(findingId, code, severity, path, differenceIds, referenceIds, null, null);
    }

    public ConfigDriftViewerFinding {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        referenceIds = referenceIds != null ? List.copyOf(referenceIds) : List.of();
    }
}
