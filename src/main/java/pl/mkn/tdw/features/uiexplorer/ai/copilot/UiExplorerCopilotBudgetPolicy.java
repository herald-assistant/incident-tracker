package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 55)
public class UiExplorerCopilotBudgetPolicy implements CopilotToolInvocationPolicy {

    static final int MAX_SEARCH_CALLS = 1;
    static final int MAX_READ_CALLS = 2;
    static final int MAX_TOTAL_GITLAB_CALLS = 3;

    private final Map<String, SessionBudget> budgets = new ConcurrentHashMap<>();

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            budgets.remove(sessionId);
        }
    }

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!uiExplorerGitLabRequest(request)) {
            return;
        }
        var budget = budgets.computeIfAbsent(request.sessionId(), ignored -> new SessionBudget());
        var total = budget.total.incrementAndGet();
        var search = GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES.equals(request.toolName())
                ? budget.search.incrementAndGet()
                : budget.search.get();
        var read = readTool(request.toolName()) ? budget.read.incrementAndGet() : budget.read.get();
        if (total > MAX_TOTAL_GITLAB_CALLS || search > MAX_SEARCH_CALLS || read > MAX_READ_CALLS) {
            throw new CopilotToolInvocationRejectedException(
                    "UI Explorer targeted fallback tool budget exceeded.",
                    Map.of(
                            "status", "denied_by_ui_explorer_budget",
                            "toolName", request.toolName(),
                            "maxSearchCalls", MAX_SEARCH_CALLS,
                            "maxReadCalls", MAX_READ_CALLS,
                            "maxTotalGitLabCalls", MAX_TOTAL_GITLAB_CALLS,
                            "retryableWithChangedArguments", false
                    )
            );
        }
    }

    private boolean uiExplorerGitLabRequest(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                && request.toolName() != null && request.toolName().startsWith(GitLabToolNames.PREFIX)
                && UiExplorerCopilotToolContextKeys.FEATURE_VALUE.equals(
                request.sessionContext().hiddenContext().get(UiExplorerCopilotToolContextKeys.FEATURE));
    }

    private boolean readTool(String toolName) {
        return GitLabToolNames.READ_REPOSITORY_FILE.equals(toolName)
                || GitLabToolNames.READ_REPOSITORY_FILE_CHUNK.equals(toolName);
    }

    private static final class SessionBudget {
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger search = new AtomicInteger();
        private final AtomicInteger read = new AtomicInteger();
    }
}
