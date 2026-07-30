package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 60)
public class RuntimeConfigurationCopilotBudgetPolicy implements CopilotToolInvocationPolicy {

    static final int MAX_GITLAB_CALLS = 6;
    static final int MAX_OPERATIONAL_CONTEXT_CALLS = 3;

    private final Map<String, SessionBudget> budgets = new ConcurrentHashMap<>();

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (request == null || request.sessionContext() == null
                || !RuntimeConfigurationCopilotToolContextKeys.FEATURE_VALUE.equals(
                request.sessionContext().hiddenContext().get(RuntimeConfigurationCopilotToolContextKeys.FEATURE))) {
            return;
        }
        var toolName = request.toolName();
        var gitLab = toolName != null && toolName.startsWith(GitLabToolNames.PREFIX);
        var operational = toolName != null && toolName.startsWith(OperationalContextToolNames.PREFIX);
        if (!gitLab && !operational) {
            return;
        }
        var budget = budgets.computeIfAbsent(request.sessionId(), ignored -> new SessionBudget());
        var actual = gitLab ? budget.gitLab.incrementAndGet() : budget.operational.incrementAndGet();
        var limit = gitLab ? MAX_GITLAB_CALLS : MAX_OPERATIONAL_CONTEXT_CALLS;
        if (actual > limit) {
            throw new CopilotToolInvocationRejectedException(
                    "Runtime Configuration Verification tool budget exceeded.",
                    Map.of(
                            "status", "denied_by_runtime_configuration_budget",
                            "toolName", toolName,
                            "limit", limit,
                            "retryableWithChangedArguments", false
                    )
            );
        }
    }

    private static final class SessionBudget {
        private final AtomicInteger gitLab = new AtomicInteger();
        private final AtomicInteger operational = new AtomicInteger();
    }
}
