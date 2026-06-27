package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkSessionCleanup implements CopilotSessionCleanup {

    private static final Duration DEFAULT_DELETE_TIMEOUT = Duration.ofSeconds(20);

    private final CopilotSessionConfigFactory sessionConfigFactory;
    private final CopilotSdkProperties properties;
    private final CopilotSessionStateDirectoryCleaner sessionStateDirectoryCleaner;

    @Override
    public void deleteSession(String sessionId, CopilotRunAuth auth) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        var normalizedSessionId = sessionId.trim();
        try {
            deleteSessionThroughSdk(normalizedSessionId, auth);
        } catch (RuntimeException exception) {
            log.warn(
                    "Copilot SDK session deletion failed sessionId={} reason={}",
                    normalizedSessionId,
                    exception.getMessage(),
                    exception
            );
        }

        sessionStateDirectoryCleaner.deleteSessionStateDirectory(normalizedSessionId);
    }

    private void deleteSessionThroughSdk(String sessionId, CopilotRunAuth auth) {
        var timeoutMs = deleteTimeout().toMillis();
        try (var client = new CopilotClient(sessionConfigFactory.clientOptions(auth))) {
            client.start().orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            try {
                client.deleteSession(sessionId).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            } finally {
                client.stop().orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            }
        }
    }

    private Duration deleteTimeout() {
        var configured = properties.getSessionDeleteTimeout();
        return configured != null && !configured.isZero() && !configured.isNegative()
                ? configured
                : DEFAULT_DELETE_TIMEOUT;
    }
}
