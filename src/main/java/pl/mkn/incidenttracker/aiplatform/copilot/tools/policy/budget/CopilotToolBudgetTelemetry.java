package pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget;

public interface CopilotToolBudgetTelemetry {

    default void onRawSqlAttempt(String sessionId) {
    }

    default void onDecision(CopilotToolBudgetDecision decision) {
    }
}
