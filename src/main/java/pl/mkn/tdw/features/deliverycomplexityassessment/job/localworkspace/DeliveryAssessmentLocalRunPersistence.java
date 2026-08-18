package pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace;

import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStateSnapshot;

@FunctionalInterface
public interface DeliveryAssessmentLocalRunPersistence {

    void persistRunSnapshot(DeliveryComplexityAssessmentJobStateSnapshot snapshot);
}
