package pl.mkn.incidenttracker.analysis.ai.copilot.execution;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AbstractSessionEvent;
import com.github.copilot.sdk.events.AssistantUsageEvent;
import com.github.copilot.sdk.events.SessionUsageInfoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparedRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceCaptureRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetRegistry;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotClientLifecycleLogger.*;
import static pl.mkn.incidenttracker.analysis.ai.copilot.execution.CopilotSessionEventLogger.*;

@Service
@Slf4j
public class CopilotSdkExecutionGateway {

    private final CopilotSdkProperties properties;
    private final CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry;
    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotToolBudgetRegistry toolBudgetRegistry;

    @Autowired
    public CopilotSdkExecutionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceCaptureRegistry toolEvidenceCaptureRegistry,
            CopilotSessionMetricsRegistry metricsRegistry,
            CopilotToolBudgetRegistry toolBudgetRegistry
    ) {
        this.properties = properties;
        this.toolEvidenceCaptureRegistry = toolEvidenceCaptureRegistry;
        this.metricsRegistry = metricsRegistry;
        this.toolBudgetRegistry = toolBudgetRegistry;
    }

    public String execute(CopilotSdkPreparedRequest preparedRequest) {
        return execute(preparedRequest, AnalysisAiToolEvidenceListener.NO_OP);
    }

    public String execute(
            CopilotSdkPreparedRequest preparedRequest,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        var overallStart = System.nanoTime();
        var correlationId = preparedRequest.correlationId();
        var copilotSessionId = preparedRequest.sessionConfig().getSessionId();
        long clientStartDurationMs = 0L;
        long createSessionDurationMs = 0L;
        long sendAndWaitDurationMs = 0L;

        try {
            try (var client = new CopilotClient(preparedRequest.clientOptions())) {
                client.onLifecycle(event -> logSession(event, correlationId));
                logClientState("before-start", client.getState(), correlationId);
                var clientStart = System.nanoTime();
                client.start().join();
                logClientState("after-start", client.getState(), correlationId);
                clientStartDurationMs = nanosToMillis(clientStart);
                logDuration("client-start", correlationId, clientStartDurationMs);

                try {
                    var createSessionStart = System.nanoTime();

                    try (var session = client.createSession(preparedRequest.sessionConfig()).join()) {
                        createSessionDurationMs = nanosToMillis(createSessionStart);
                        logDuration("create-session", correlationId, createSessionDurationMs);
                        var sessionSummary = newSessionLogSummary(correlationId);

                        toolEvidenceCaptureRegistry.registerSession(
                                session.getSessionId(),
                                toolEvidenceListener
                        );
                        toolBudgetRegistry.registerSession(session.getSessionId());

                        session.on(event -> handleSessionEvent(event, session, sessionSummary));

                        try {
                            var sendAndWaitStart = System.nanoTime();
                            var timeoutMs = sendAndWaitTimeoutMs();
                            log.info(
                                    "Copilot sendAndWait configuration correlationId={} timeoutMs={}",
                                    correlationId,
                                    timeoutMs
                            );
                            var response = session.sendAndWait(preparedRequest.messageOptions(), timeoutMs).join();

                            sendAndWaitDurationMs = nanosToMillis(sendAndWaitStart);
                            logDuration("send-and-wait", correlationId, sendAndWaitDurationMs);
                            var content = response.getData() != null ? response.getData().content() : null;
                            if (content == null || content.isBlank()) {
                                throw new CopilotSdkInvocationException("Copilot SDK returned an empty assistant response.");
                            }

                            logSessionSummary(session.getSessionId(), sessionSummary, nanosToMillis(overallStart));
                            return content;
                        } finally {
                            toolEvidenceCaptureRegistry.unregisterSession(session.getSessionId());
                            toolBudgetRegistry.unregisterSession(session.getSessionId()).ifPresent(snapshot -> log.info(
                                    "Copilot tool budget summary sessionId={} totalCalls={} softLimitExceeded={} deniedToolCalls={} rawSqlAttempts={}",
                                    snapshot.sessionId(),
                                    snapshot.totalCalls(),
                                    snapshot.softLimitExceededCount(),
                                    snapshot.deniedToolCalls(),
                                    snapshot.rawSqlAttempts()
                            ));
                        }
                    }
                } finally {
                    logClientState("before-stop", client.getState(), correlationId);

                    var clientStop = System.nanoTime();
                    client.stop().join();

                    logClientState("after-stop", client.getState(), correlationId);
                    logDuration("client-stop", correlationId, nanosToMillis(clientStop));
                }
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
        } finally {
            metricsRegistry.recordExecutionDurations(
                    copilotSessionId,
                    clientStartDurationMs,
                    createSessionDurationMs,
                    sendAndWaitDurationMs,
                    nanosToMillis(overallStart)
            );
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

    private void handleSessionEvent(
            AbstractSessionEvent event,
            CopilotSession session,
            SessionLogSummary sessionSummary
    ) {
        logSessionEvent(event, session, sessionSummary);
        recordUsageEvent(event, session.getSessionId());
    }

    private void recordUsageEvent(AbstractSessionEvent event, String sessionId) {
        if (event instanceof AssistantUsageEvent assistantUsageEvent) {
            var data = assistantUsageEvent.getData();
            if (data == null) {
                return;
            }

            metricsRegistry.recordAssistantUsage(
                    sessionId,
                    data.model(),
                    data.inputTokens(),
                    data.outputTokens(),
                    data.cacheReadTokens(),
                    data.cacheWriteTokens(),
                    data.cost(),
                    data.duration()
            );
            return;
        }

        if (event instanceof SessionUsageInfoEvent sessionUsageInfoEvent) {
            var data = sessionUsageInfoEvent.getData();
            if (data == null) {
                return;
            }

            metricsRegistry.recordSessionUsageInfo(
                    sessionId,
                    data.tokenLimit(),
                    data.currentTokens(),
                    data.messagesLength()
            );
        }
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
