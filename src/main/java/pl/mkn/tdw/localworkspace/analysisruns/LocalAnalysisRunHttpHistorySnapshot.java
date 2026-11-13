package pl.mkn.tdw.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;

/**
 * Private, immutable observation; never part of the portable export envelope.
 */
public record LocalAnalysisRunHttpHistorySnapshot(
        String status,
        ArrayNode entries,
        Instant capturedAt,
        String source,
        int redactions,
        String reason
) {
    public LocalAnalysisRunHttpHistorySnapshot {
        entries = entries == null ? null : entries.deepCopy();
    }

    @Override
    public ArrayNode entries() {
        return entries == null ? null : entries.deepCopy();
    }

    public boolean available() {
        return "AVAILABLE".equals(status) && entries != null;
    }
}
