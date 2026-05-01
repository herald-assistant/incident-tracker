package pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.session;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CopilotToolSessionValidationPolicy implements CopilotToolInvocationPolicy {

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (request == null
                || request.sessionContext() == null
                || !StringUtils.hasText(request.sessionContext().copilotSessionId())
                || !StringUtils.hasText(request.sessionId())) {
            return;
        }

        if (!request.sessionContext().copilotSessionId().equals(request.sessionId())) {
            throw new IllegalStateException(
                    "Copilot tool invocation sessionId mismatch. expected=%s actual=%s tool=%s"
                            .formatted(
                                    request.sessionContext().copilotSessionId(),
                                    request.sessionId(),
                                    request.toolName()
                            )
            );
        }
    }
}
