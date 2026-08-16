package pl.mkn.tdw.features.uiexplorer.job.export;

import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;

import java.time.Instant;

public record UiExplorerExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.ui-explorer-export";
    public static final int VERSION = 4;
    public static final String PAYLOAD_TYPE = "ui-explorer-analysis";
    public static final String RESULT_CONTRACT = "ui-explorer-result-v4";

    public static UiExplorerExportEnvelope from(
            UiExplorerJobStateSnapshot sanitizedSnapshot,
            Instant exportedAt
    ) {
        return new UiExplorerExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, RESULT_CONTRACT, sanitizedSnapshot)
        );
    }

    public record Payload(
            String type,
            String resultContract,
            UiExplorerJobStateSnapshot job
    ) {
    }
}
