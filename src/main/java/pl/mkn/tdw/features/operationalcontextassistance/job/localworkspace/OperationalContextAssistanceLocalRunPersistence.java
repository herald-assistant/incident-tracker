package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;

import java.util.Optional;
import java.util.Set;

@FunctionalInterface
public interface OperationalContextAssistanceLocalRunPersistence {

    OperationalContextAssistanceLocalRunPersistence NO_OP = (snapshot, request, requiredRepositoryScopeIds) -> { };

    void persistRunSnapshot(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request,
            Set<String> requiredRepositoryScopeIds
    );

    default Optional<OperationalContextAssistanceStoredRun> findRestorable(String jobId) {
        return Optional.empty();
    }
}
