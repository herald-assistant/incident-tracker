package pl.mkn.tdw.features.incidentanalysis.job.export;

import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;

import java.time.Instant;

public record IncidentAnalysisExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {
    public static final String SCHEMA = "tdw.analysis-export";
    public static final int VERSION = 6;
    public static final String PAYLOAD_TYPE = "analysis-job";

    public IncidentAnalysisExportEnvelope {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    public static IncidentAnalysisExportEnvelope from(
            AnalysisJobStateSnapshot job,
            Instant exportedAt
    ) {
        return new IncidentAnalysisExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(PAYLOAD_TYPE, job)
        );
    }

    public record Payload(
            String type,
            AnalysisJobStateSnapshot job
    ) {
        public Payload {
            if (job == null) {
                throw new IllegalArgumentException("job is required");
            }
        }
    }
}
