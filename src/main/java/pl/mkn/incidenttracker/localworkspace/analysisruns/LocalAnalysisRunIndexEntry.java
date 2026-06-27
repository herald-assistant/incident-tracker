package pl.mkn.incidenttracker.localworkspace.analysisruns;

import java.time.Instant;

public record LocalAnalysisRunIndexEntry(
        String analysisId,
        String schema,
        int version,
        String runPath,
        String feature,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public LocalAnalysisRunIndexEntry {
        analysisId = required("analysisId", analysisId);
        schema = blankToDefault(schema, LocalAnalysisRunRecord.SCHEMA);
        version = version <= 0 ? LocalAnalysisRunRecord.VERSION : version;
        runPath = required("runPath", runPath);
        feature = required("feature", feature);
        name = blankToDefault(name, analysisId);
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    public LocalAnalysisRunIndexEntry withName(String newName, Instant updatedAt) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                schema,
                version,
                runPath,
                feature,
                newName,
                createdAt,
                updatedAt,
                completedAt
        );
    }

    public LocalAnalysisRunIndexEntry withRunPath(String runPath) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                schema,
                version,
                runPath,
                feature,
                name,
                createdAt,
                updatedAt,
                completedAt
        );
    }

    public LocalAnalysisRunIndexEntry withUpdatedAt(Instant updatedAt) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                schema,
                version,
                runPath,
                feature,
                name,
                createdAt,
                updatedAt,
                completedAt
        );
    }

    private static String required(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
