package pl.mkn.tdw.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Writes the neutral run record; each feature supplies its own export envelope and label. */
public final class LocalAnalysisRunSnapshotWriter {

    private LocalAnalysisRunSnapshotWriter() {
    }

    public static void save(LocalAnalysisRunStore store, JsonNode envelope,
                            LocalAnalysisRunContinuation continuation, Metadata metadata) {
        var record = LocalAnalysisRunRecord.v1(envelope, continuation);
        var index = new LocalAnalysisRunIndexEntry(
                metadata.runId(), LocalAnalysisRunRecord.SCHEMA, LocalAnalysisRunRecord.VERSION,
                "runs/" + metadata.runId() + "/run.json", metadata.feature(), metadata.displayName(),
                metadata.status(), metadata.createdAt(), metadata.updatedAt(), metadata.completedAt());
        store.save(index, record);
    }

    public static Instant exportTimestamp(Instant createdAt, Instant updatedAt, Instant completedAt) {
        return completedAt != null ? completedAt : updatedAt != null ? updatedAt : createdAt;
    }

    public record Metadata(String runId, String feature, String displayName, String status,
                           Instant createdAt, Instant updatedAt, Instant completedAt) {
    }
}
