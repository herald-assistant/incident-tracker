package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;

import java.time.Instant;

public record OperationalContextAssistanceExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        OperationalContextAssistanceMode mode,
        OperationalContextAssistanceJobStartRequest.Target target,
        OperationalContextAssistanceJobSnapshot job
) {
    public static final String SCHEMA = "tdw.operational-context-assistance-export";
    public static final int VERSION = 1;

    public static OperationalContextAssistanceExportEnvelope from(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request
    ) {
        return new OperationalContextAssistanceExportEnvelope(
                SCHEMA,
                VERSION,
                snapshot.updatedAt() != null ? snapshot.updatedAt() : snapshot.createdAt(),
                request != null ? request.mode() : null,
                request != null ? request.target() : null,
                snapshot
        );
    }
}
