package pl.mkn.tdw.features.deliveryscopecomplexity.job.localworkspace;

import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStateSnapshot;

@FunctionalInterface
public interface DeliveryScopeLocalRunPersistence {

    void persistRunSnapshot(DeliveryScopeComplexityJobStateSnapshot snapshot);
}
