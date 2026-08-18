package pl.mkn.tdw.features.deliverycomplexityassessment.job.export;

import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStateSnapshot;

import java.time.Instant;

public record DeliveryComplexityAssessmentExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.delivery-complexity-assessment-export";
    public static final int VERSION = 2;
    public static final String PAYLOAD_TYPE = "delivery-complexity-assessment";
    public static final String RESULT_CONTRACT = "delivery-complexity-assessment-v2";

    public static DeliveryComplexityAssessmentExportEnvelope from(
            DeliveryComplexityAssessmentJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new DeliveryComplexityAssessmentExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, RESULT_CONTRACT, snapshot)
        );
    }

    public record Payload(
            String type,
            String resultContract,
            DeliveryComplexityAssessmentJobStateSnapshot job
    ) {
    }
}
