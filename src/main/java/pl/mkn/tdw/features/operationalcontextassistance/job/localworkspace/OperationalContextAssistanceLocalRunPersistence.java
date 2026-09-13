package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;

@FunctionalInterface
public interface OperationalContextAssistanceLocalRunPersistence {

    OperationalContextAssistanceLocalRunPersistence NO_OP = (snapshot, request) -> { };

    void persistRunSnapshot(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request
    );
}
