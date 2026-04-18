package pl.mkn.incidenttracker.analysis.ai.copilot.execution;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.json.SessionLifecycleEvent;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class CopilotClientLifecycleLogger {

    public void logSession(SessionLifecycleEvent event, String correlationId) {
        if (event == null) {
            return;
        }

        var metadata = event.getMetadata();
        var summary = metadata != null ? metadata.summary() : null;

        if ("session.updated".equals(event.getType()) && (summary == null || summary.isBlank())) {
            log.debug(
                    "Copilot client lifecycle event type={} correlationId={} sessionId={} startTime={} modifiedTime={} summary={}",
                    event.getType(),
                    correlationId,
                    event.getSessionId(),
                    metadata != null ? metadata.startTime() : null,
                    metadata != null ? metadata.modifiedTime() : null,
                    null
            );
            return;
        }

        log.info(
                "Copilot client lifecycle event type={} correlationId={} sessionId={} startTime={} modifiedTime={} summary={}",
                event.getType(),
                correlationId,
                event.getSessionId(),
                metadata != null ? metadata.startTime() : null,
                metadata != null ? metadata.modifiedTime() : null,
                abbreviate(summary, 300)
        );
    }

    public void logClientState(String action, ConnectionState state, String correlationId) {
        log.info("Copilot client state action={} correlationId={} state={}", action, correlationId, state);
    }

    public void logDuration(String stage, String correlationId, long durationMs) {
        log.info("Copilot execution timing stage={} correlationId={} durationMs={}", stage, correlationId, durationMs);
    }

    public long nanosToMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }

}
