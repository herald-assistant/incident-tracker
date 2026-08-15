package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;

import java.time.Instant;

public record UiExplorerLocalRunEnvelope(
        String schema,
        int version,
        Instant storedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.ui-explorer-local-run";
    public static final int VERSION = 1;
    public static final String PAYLOAD_TYPE = "ui-explorer-analysis";
    public static final String RESULT_CONTRACT = "ui-explorer-result-v1";

    public static UiExplorerLocalRunEnvelope from(
            UiExplorerJobStateSnapshot sanitizedSnapshot,
            Instant storedAt
    ) {
        return new UiExplorerLocalRunEnvelope(
                SCHEMA,
                VERSION,
                storedAt != null ? storedAt : Instant.now(),
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
