package pl.mkn.tdw.localworkspace.analysisruns;

import java.time.Instant;

public record LocalAnalysisRunChatResult(
        LocalAnalysisRunRecord record,
        Instant updatedAt
) {
    public LocalAnalysisRunChatResult {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
