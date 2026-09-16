package pl.mkn.tdw.features.uxinspector.job.export;

import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot;

import java.time.Instant;

public record UxInspectorExportEnvelope(String schema, int version, Instant exportedAt, Payload payload) {
    public static final String SCHEMA = "tdw.ux-inspector-export";
    public static final int VERSION = 1;
    public static final String PAYLOAD_TYPE = "ux-inspector-analysis";
    public static final String RESULT_CONTRACT = "ux-inspector-result-v1";

    public static UxInspectorExportEnvelope from(UxInspectorJobStateSnapshot job, Instant exportedAt) {
        return new UxInspectorExportEnvelope(SCHEMA, VERSION, exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, RESULT_CONTRACT, job));
    }
    public record Payload(String type, String resultContract, UxInspectorJobStateSnapshot job) {}
}
