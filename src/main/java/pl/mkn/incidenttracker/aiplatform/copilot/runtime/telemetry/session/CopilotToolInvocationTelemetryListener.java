package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.telemetry.CopilotToolMetrics;

@Component
@RequiredArgsConstructor
public class CopilotToolInvocationTelemetryListener {

    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotMetricsLogger metricsLogger;

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (event.outcome() == CopilotToolInvocationOutcome.REJECTED) {
            return;
        }

        recordToolMetrics(
                event.sessionContext(),
                event.sessionId(),
                event.toolCallId(),
                event.toolName(),
                event.latencyMs(),
                event.outcome() == CopilotToolInvocationOutcome.COMPLETED ? event.rawResult() : null
        );
    }

    private void recordToolMetrics(
            CopilotToolSessionContext sessionContext,
            String sessionId,
            String toolCallId,
            String toolName,
            long latencyMs,
            String rawResult
    ) {
        if (sessionContext == null) {
            return;
        }

        var effectiveSessionId = StringUtils.hasText(sessionContext.copilotSessionId())
                ? sessionContext.copilotSessionId()
                : sessionId;
        var toolMetrics = CopilotToolMetrics.from(
                sessionContext.analysisRunId(),
                effectiveSessionId,
                toolCallId,
                toolName,
                latencyMs,
                rawResult
        );
        metricsRegistry.recordToolCall(toolMetrics);
        metricsLogger.logToolEvent(toolMetrics);
    }
}
