package pl.mkn.tdw.features.deliveryscopecomplexity.job.export;

import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStateSnapshot;

import java.time.Instant;

public record DeliveryScopeComplexityExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.delivery-scope-complexity-export";
    public static final int VERSION = 1;
    public static final String PAYLOAD_TYPE = "delivery-scope-complexity";
    public static final String RESULT_CONTRACT = "delivery-scope-complexity-v1";

    public static DeliveryScopeComplexityExportEnvelope from(
            DeliveryScopeComplexityJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new DeliveryScopeComplexityExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, RESULT_CONTRACT, snapshot)
        );
    }

    public record Payload(
            String type,
            String resultContract,
            DeliveryScopeComplexityJobStateSnapshot job
    ) {
    }
}
