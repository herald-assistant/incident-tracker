package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class CopilotClientShutdown {

    private static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(20);

    private final CopilotSdkProperties properties;

    public void stop(CopilotClient client, String runReference) {
        var reference = StringUtils.hasText(runReference) ? runReference : "unassigned";
        var timeoutMs = stopTimeout().toMillis();

        try {
            client.stop().orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
        } catch (RuntimeException stopException) {
            log.warn(
                    "Graceful Copilot client stop failed runReference={} timeoutMs={} reason={}; forcing stop",
                    reference,
                    timeoutMs,
                    stopException.getMessage()
            );
            forceStop(client, reference, timeoutMs, stopException);
        }
    }

    private void forceStop(
            CopilotClient client,
            String runReference,
            long timeoutMs,
            RuntimeException stopException
    ) {
        try {
            client.forceStop().orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
        } catch (RuntimeException forceStopException) {
            forceStopException.addSuppressed(stopException);
            throw new IllegalStateException(
                    "Failed to stop Copilot client for run " + runReference + ".",
                    forceStopException
            );
        }
    }

    private Duration stopTimeout() {
        var configured = properties.getClientStopTimeout();
        return configured != null && !configured.isZero() && !configured.isNegative()
                ? configured
                : DEFAULT_STOP_TIMEOUT;
    }
}
