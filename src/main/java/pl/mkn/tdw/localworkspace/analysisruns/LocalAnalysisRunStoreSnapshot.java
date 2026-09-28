package pl.mkn.tdw.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Optional runtime state captured for a run, independent of its feature export envelope. */
public record LocalAnalysisRunStoreSnapshot(JsonNode state, Instant capturedAt, String source, int redactions) {
    public LocalAnalysisRunStoreSnapshot {
        if (state == null || (!state.isObject() && !state.isArray())) {
            throw new IllegalArgumentException("Store snapshot state must be a JSON object or array.");
        }
        state = state.deepCopy();
    }
}
