package pl.mkn.tdw.features.runtimeconfigurationverification.job.export;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;

import java.time.Instant;

public record RuntimeConfigurationVerificationExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {

    public static final String SCHEMA = "tdw.runtime-configuration-verification-export";
    public static final int VERSION = 1;
    public static final String PAYLOAD_TYPE = "runtime-configuration-verification-analysis";
    public static final String RESULT_CONTRACT = "runtime-configuration-verification-result-v1";

    public static RuntimeConfigurationVerificationExportEnvelope from(
            RuntimeConfigurationVerificationJobStateSnapshot snapshot,
            Instant exportedAt
    ) {
        return new RuntimeConfigurationVerificationExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(
                        PAYLOAD_TYPE,
                        RESULT_CONTRACT,
                        RuntimeConfigurationVerificationSnapshotSanitizer.sanitize(snapshot)
                )
        );
    }

    public record Payload(
            String type,
            String resultContract,
            RuntimeConfigurationVerificationJobStateSnapshot job
    ) {
    }
}
