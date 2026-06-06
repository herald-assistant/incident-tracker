package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import com.github.copilot.ConnectionState;
import com.github.copilot.rpc.SessionLifecycleEvent;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class CopilotClientLifecycleLogger {

    public void logSession(SessionLifecycleEvent event, String runReference) {
        if (event == null) {
            return;
        }

        var metadata = event.getMetadata();
        var summary = metadata != null ? metadata.summary() : null;

        if ("session.updated".equals(event.getType()) && (summary == null || summary.isBlank())) {
            log.debug(
                    "Copilot client lifecycle event type={} runReference={} sessionId={} startTime={} modifiedTime={} summary={}",
                    event.getType(),
                    runReference,
                    event.getSessionId(),
                    metadata != null ? metadata.startTime() : null,
                    metadata != null ? metadata.modifiedTime() : null,
                    null
            );
            return;
        }

        log.info(
                "Copilot client lifecycle event type={} runReference={} sessionId={} startTime={} modifiedTime={} summary={}",
                event.getType(),
                runReference,
                event.getSessionId(),
                metadata != null ? metadata.startTime() : null,
                metadata != null ? metadata.modifiedTime() : null,
                abbreviate(summary, 300)
        );
    }

    public void logClientState(String action, ConnectionState state, String runReference) {
        log.info("Copilot client state action={} runReference={} state={}", action, runReference, state);
    }

    public void logDuration(String stage, String runReference, long durationMs) {
        log.info("Copilot execution timing stage={} runReference={} durationMs={}", stage, runReference, durationMs);
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
