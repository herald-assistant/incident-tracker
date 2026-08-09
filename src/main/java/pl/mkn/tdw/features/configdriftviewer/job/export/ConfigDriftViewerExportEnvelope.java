package pl.mkn.tdw.features.configdriftviewer.job.export;

import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;

import java.time.Instant;

public record ConfigDriftViewerExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.config-drift-viewer-export";
    public static final int VERSION = 1;
    public static final String PAYLOAD_TYPE = "config-drift-viewer-analysis";
    public static final String RESULT_CONTRACT = "config-drift-viewer-result-v1";

    public static ConfigDriftViewerExportEnvelope from(
            ConfigDriftViewerJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new ConfigDriftViewerExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(
                        PAYLOAD_TYPE,
                        RESULT_CONTRACT,
                        ConfigDriftViewerSnapshotSanitizer.sanitize(snapshot)
                )
        );
    }

    public record Payload(
            String type,
            String resultContract,
            ConfigDriftViewerJobStateSnapshot job
    ) {
    }
}
