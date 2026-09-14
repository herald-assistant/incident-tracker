package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record OperationalContextAssistanceExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        OperationalContextAssistanceMode mode,
        OperationalContextAssistanceJobStartRequest.Target target,
        OperationalContextAssistanceJobSnapshot job,
        List<String> requiredRepositoryScopeIds
) {
    public static final String SCHEMA = "tdw.operational-context-assistance-export";
    public static final int VERSION = 2;

    public static OperationalContextAssistanceExportEnvelope from(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request,
            Set<String> requiredRepositoryScopeIds,
            OperationalContextAssistanceExportEnvelope previous
    ) {
        return new OperationalContextAssistanceExportEnvelope(
                SCHEMA,
                VERSION,
                snapshot.updatedAt() != null ? snapshot.updatedAt() : snapshot.createdAt(),
                request != null ? request.mode() : previous != null ? previous.mode() : null,
                request != null ? request.target() : previous != null ? previous.target() : null,
                snapshot,
                requiredRepositoryScopeIds.stream().sorted().toList()
        );
    }
}
