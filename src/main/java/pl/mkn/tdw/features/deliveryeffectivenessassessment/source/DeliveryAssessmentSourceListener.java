package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

public interface DeliveryAssessmentSourceListener {

    DeliveryAssessmentSourceListener NO_OP = new DeliveryAssessmentSourceListener() {
    };

    default void onSearchCompleted(int discovered, int total, String effectiveJql) {
    }

    default void onIssueProcessed(int completed, int total, String issueKey) {
    }
}
