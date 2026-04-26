package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class CopilotToolBudgetRegistry {

    private final CopilotToolBudgetProperties properties;
    private final Map<String, CopilotToolBudgetState> stateBySessionId = new ConcurrentHashMap<>();

    public CopilotToolBudgetState registerSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("copilotSessionId is required to register a tool budget session.");
        }
        return stateBySessionId.computeIfAbsent(
                sessionId,
                ignored -> new CopilotToolBudgetState(sessionId, properties)
        );
    }

    public Optional<CopilotToolBudgetState> state(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stateBySessionId.get(sessionId));
    }

    public Optional<CopilotToolBudgetState.CopilotToolBudgetSnapshot> unregisterSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stateBySessionId.remove(sessionId))
                .map(CopilotToolBudgetState::snapshot);
    }

    public int sessionCount() {
        return stateBySessionId.size();
    }
}
