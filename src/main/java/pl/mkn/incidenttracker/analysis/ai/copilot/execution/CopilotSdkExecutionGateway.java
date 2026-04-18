package pl.mkn.incidenttracker.analysis.ai.copilot.execution;

import com.github.copilot.sdk.CopilotClient;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotClientLifecycleLogger.*;
import static pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSessionEventLogger.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkExecutionGateway {

    private final CopilotSdkProperties properties;

    public String execute(CopilotSdkPreparedRequest preparedRequest) {
        var overallStart = System.nanoTime();
        var correlationId = preparedRequest.correlationId();

        try (var client = new CopilotClient(preparedRequest.clientOptions())) {
            client.onLifecycle(event -> logSession(event, correlationId));
            logClientState("before-start", client.getState(), correlationId);
            var clientStart = System.nanoTime();
            client.start().join();
            logClientState("after-start", client.getState(), correlationId);
            logDuration("client-start", correlationId, nanosToMillis(clientStart));

            try {
                var createSessionStart = System.nanoTime();

                try (var session = client.createSession(preparedRequest.sessionConfig()).join()) {
                    logDuration("create-session", correlationId, nanosToMillis(createSessionStart));
                    var sessionSummary = newSessionLogSummary(correlationId);

                    session.on(event -> logSessionEvent(event, session, sessionSummary));

                    var sendAndWaitStart = System.nanoTime();
                    var timeoutMs = sendAndWaitTimeoutMs();
                    log.info(
                            "Copilot sendAndWait configuration correlationId={} timeoutMs={}",
                            correlationId,
                            timeoutMs
                    );
                    var response = session.sendAndWait(preparedRequest.messageOptions(), timeoutMs).join();

                    logDuration("send-and-wait", correlationId, nanosToMillis(sendAndWaitStart));
                    var content = response.getData() != null ? response.getData().content() : null;
                    if (content == null || content.isBlank()) {
                        throw new CopilotSdkInvocationException("Copilot SDK returned an empty assistant response.");
                    }

                    logSessionSummary(session.getSessionId(), sessionSummary, nanosToMillis(overallStart));
                    return content;
                }
            } finally {
                logClientState("before-stop", client.getState(), correlationId);

                var clientStop = System.nanoTime();
                client.stop().join();

                logClientState("after-stop", client.getState(), correlationId);
                logDuration("client-stop", correlationId, nanosToMillis(clientStop));
            }
        } catch (CopilotSdkInvocationException exception) {
            throw exception;
        } catch (Exception exception) {
            var rootCause = unwrapCompletionException(exception);
            log.error(
                    "Copilot SDK invocation failed correlationId={} exceptionType={} rootCauseType={} rootCauseMessage={}",
                    correlationId,
                    exception.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    exception
            );
            throw new CopilotSdkInvocationException(buildFailureMessage(rootCause), exception);
        }
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        var current = throwable;

        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    private String buildFailureMessage(Throwable rootCause) {
        var rootMessage = rootCause.getMessage();
        if (rootMessage == null || rootMessage.isBlank()) {
            return "Copilot SDK invocation failed.";
        }

        return "Copilot SDK invocation failed: " + rootMessage.trim();
    }

    private long sendAndWaitTimeoutMs() {
        var configuredTimeout = properties.getSendAndWaitTimeout();
        return configuredTimeout != null ? configuredTimeout.toMillis() : 300_000L;
    }

}
