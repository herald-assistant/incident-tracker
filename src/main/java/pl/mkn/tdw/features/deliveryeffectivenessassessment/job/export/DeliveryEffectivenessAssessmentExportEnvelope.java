package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.export;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStateSnapshot;

import java.time.Instant;

public record DeliveryEffectivenessAssessmentExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.delivery-effectiveness-assessment-export";
    public static final int VERSION = 2;
    public static final String PAYLOAD_TYPE = "delivery-effectiveness-assessment";
    public static final String RESULT_CONTRACT = "delivery-effectiveness-assessment-v2";

    public static DeliveryEffectivenessAssessmentExportEnvelope from(
            DeliveryEffectivenessAssessmentJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new DeliveryEffectivenessAssessmentExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, RESULT_CONTRACT, snapshot)
        );
    }

    public record Payload(
            String type,
            String resultContract,
            DeliveryEffectivenessAssessmentJobStateSnapshot job
    ) {
    }
}
