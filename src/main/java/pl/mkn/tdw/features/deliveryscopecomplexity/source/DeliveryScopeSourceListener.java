package pl.mkn.tdw.features.deliveryscopecomplexity.source;

public interface DeliveryScopeSourceListener {

    DeliveryScopeSourceListener NO_OP = new DeliveryScopeSourceListener() {
    };

    default void onSearchCompleted(int discovered, int total, String effectiveJql) {
    }

    default void onIssueProcessed(int completed, int total, String issueKey) {
    }
}
