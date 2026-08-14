package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.localworkspace;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStateSnapshot;

@FunctionalInterface
public interface DeliveryAssessmentLocalRunPersistence {

    void persistRunSnapshot(DeliveryEffectivenessAssessmentJobStateSnapshot snapshot);
}
