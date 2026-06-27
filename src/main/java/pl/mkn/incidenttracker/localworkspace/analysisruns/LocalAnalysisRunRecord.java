package pl.mkn.incidenttracker.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;

public record LocalAnalysisRunRecord(
        String schema,
        int version,
        JsonNode exportEnvelope,
        LocalAnalysisRunContinuation continuation
) {
    public static final String SCHEMA = "tdw.local-analysis-run";
    public static final int VERSION = 1;

    public LocalAnalysisRunRecord {
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported local analysis run schema: " + schema);
        }
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported local analysis run version: " + version);
        }
        if (exportEnvelope == null || exportEnvelope.isNull()) {
            throw new IllegalArgumentException("exportEnvelope is required");
        }
        if (continuation == null) {
            continuation = new LocalAnalysisRunContinuation(false, null, null, null, null, null, null);
        }
    }

    public static LocalAnalysisRunRecord v1(
            JsonNode exportEnvelope,
            LocalAnalysisRunContinuation continuation
    ) {
        return new LocalAnalysisRunRecord(SCHEMA, VERSION, exportEnvelope, continuation);
    }
}
