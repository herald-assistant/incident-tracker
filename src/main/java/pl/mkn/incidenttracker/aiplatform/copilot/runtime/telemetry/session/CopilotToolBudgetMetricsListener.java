package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetDecision;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetTelemetry;

@Component
@RequiredArgsConstructor
public class CopilotToolBudgetMetricsListener implements CopilotToolBudgetTelemetry {

    private final CopilotSessionMetricsRegistry metricsRegistry;

    @Override
    public void onRawSqlAttempt(String sessionId) {
        metricsRegistry.recordBudgetRawSqlAttempt(sessionId);
    }

    @Override
    public void onDecision(CopilotToolBudgetDecision decision) {
        if (decision == null) {
            return;
        }
        if (decision.denied()) {
            metricsRegistry.recordBudgetDenied(decision.sessionId(), decision.toolName(), decision.reason());
        }
        if (decision.softLimitExceeded()) {
            metricsRegistry.recordBudgetWarnings(decision.sessionId(), decision.warnings());
        }
    }
}
