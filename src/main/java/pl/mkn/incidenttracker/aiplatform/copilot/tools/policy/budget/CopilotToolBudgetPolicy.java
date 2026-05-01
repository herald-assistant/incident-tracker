package pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyResult;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;
import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotToolBudgetPolicy implements CopilotToolInvocationPolicy {

    private final CopilotToolBudgetRegistry budgetRegistry;
    private final List<CopilotToolBudgetTelemetry> telemetryListeners;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        var decision = beforeInvocation(request.sessionId(), request.toolName(), request.rawArguments());
        if (decision.denied()) {
            throw new CopilotToolInvocationRejectedException(
                    decision.reason(),
                    CopilotToolBudgetDtos.deniedResult(decision)
            );
        }
    }

    @Override
    public void afterInvocation(CopilotToolInvocationPolicyResult result) {
        afterInvocation(result.sessionId(), result.toolName(), result.rawResult());
    }

    public CopilotToolBudgetDecision beforeInvocation(
            String sessionId,
            String toolName,
            String argumentsJson
    ) {
        if (DatabaseToolNames.EXECUTE_READONLY_SQL.equals(toolName)) {
            telemetryListeners.forEach(listener -> listener.onRawSqlAttempt(sessionId));
        }
        var decision = budgetRegistry.state(sessionId)
                .map(state -> state.beforeInvocation(toolName))
                .orElseGet(() -> CopilotToolBudgetDecision.allowed(sessionId, toolName));
        recordDecision(decision);
        if (decision.denied()) {
            log.warn(
                    "Copilot tool budget denied sessionId={} toolName={} reason={} arguments={}",
                    sessionId,
                    toolName,
                    decision.reason(),
                    abbreviate(argumentsJson, 500)
            );
        } else if (decision.softLimitExceeded()) {
            log.warn(
                    "Copilot tool budget soft warning sessionId={} toolName={} warnings={} arguments={}",
                    sessionId,
                    toolName,
                    decision.warnings(),
                    abbreviate(argumentsJson, 500)
            );
        }
        return decision;
    }

    public CopilotToolBudgetDecision afterInvocation(
            String sessionId,
            String toolName,
            String rawResult
    ) {
        var decision = budgetRegistry.state(sessionId)
                .map(state -> state.afterInvocation(toolName, rawResult))
                .orElseGet(() -> CopilotToolBudgetDecision.allowed(sessionId, toolName));
        recordDecision(decision);
        if (decision.softLimitExceeded()) {
            log.warn(
                    "Copilot tool budget post-call warning sessionId={} toolName={} warnings={} rawResultLength={}",
                    sessionId,
                    toolName,
                    decision.warnings(),
                    rawResult != null ? rawResult.length() : 0
            );
        }
        return decision;
    }

    private void recordDecision(CopilotToolBudgetDecision decision) {
        telemetryListeners.forEach(listener -> listener.onDecision(decision));
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }
}
