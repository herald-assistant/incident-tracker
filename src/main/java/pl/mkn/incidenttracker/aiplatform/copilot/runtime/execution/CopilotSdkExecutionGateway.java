package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AbstractSessionEvent;
import com.github.copilot.sdk.events.AssistantUsageEvent;
import com.github.copilot.sdk.events.SessionUsageInfoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotClientLifecycleLogger.*;
import static pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSessionEventLogger.*;

@Service
@Slf4j
public class CopilotSdkExecutionGateway {

    private final CopilotSdkProperties properties;
    private final CopilotToolEvidenceSessionStore toolEvidenceSessionStore;
    private final CopilotSessionExecutionMetricsRecorder metricsRecorder;
    private final CopilotToolBudgetRegistry toolBudgetRegistry;

    @Autowired
    public CopilotSdkExecutionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotSessionExecutionMetricsRecorder metricsRecorder,
            CopilotToolBudgetRegistry toolBudgetRegistry
    ) {
        this.properties = properties;
        this.toolEvidenceSessionStore = toolEvidenceSessionStore;
        this.metricsRecorder = metricsRecorder;
        this.toolBudgetRegistry = toolBudgetRegistry;
    }

    public String execute(CopilotPreparedSession preparedSession) {
        var overallStart = System.nanoTime();
        var runReference = preparedSession.runReference();
        var copilotSessionId = preparedSession.sessionConfig().getSessionId();
        long clientStartDurationMs = 0L;
        long createSessionDurationMs = 0L;
        long sendAndWaitDurationMs = 0L;

        try {
            try (var client = new CopilotClient(preparedSession.clientOptions())) {
                client.onLifecycle(event -> logSession(event, runReference));
                logClientState("before-start", client.getState(), runReference);
                var clientStart = System.nanoTime();
                client.start().join();
                logClientState("after-start", client.getState(), runReference);
                clientStartDurationMs = nanosToMillis(clientStart);
                logDuration("client-start", runReference, clientStartDurationMs);

                try {
                    var createSessionStart = System.nanoTime();

                    try (var session = client.createSession(preparedSession.sessionConfig()).join()) {
                        createSessionDurationMs = nanosToMillis(createSessionStart);
                        logDuration("create-session", runReference, createSessionDurationMs);
                        var sessionSummary = newSessionLogSummary(runReference);

                        toolEvidenceSessionStore.registerSession(
                                session.getSessionId(),
                                preparedSession.evidenceSink()
                        );
                        toolBudgetRegistry.registerSession(session.getSessionId());

                        session.on(event -> handleSessionEvent(event, session, sessionSummary));

                        try {
                            var sendAndWaitStart = System.nanoTime();
                            var timeoutMs = sendAndWaitTimeoutMs();
                            log.info(
                                    "Copilot sendAndWait configuration runReference={} timeoutMs={}",
                                    runReference,
                                    timeoutMs
                            );
                            var response = session.sendAndWait(preparedSession.messageOptions(), timeoutMs).join();

                            sendAndWaitDurationMs = nanosToMillis(sendAndWaitStart);
                            logDuration("send-and-wait", runReference, sendAndWaitDurationMs);
                            var content = response.getData() != null ? response.getData().content() : null;
                            if (content == null || content.isBlank()) {
                                throw new CopilotSdkInvocationException("Copilot SDK returned an empty assistant response.");
                            }

                            logSessionSummary(session.getSessionId(), sessionSummary, nanosToMillis(overallStart));
                            return content;
                        } finally {
                            toolEvidenceSessionStore.unregisterSession(session.getSessionId());
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
                    logClientState("before-stop", client.getState(), runReference);

                    var clientStop = System.nanoTime();
                    client.stop().join();

                    logClientState("after-stop", client.getState(), runReference);
                    logDuration("client-stop", runReference, nanosToMillis(clientStop));
                }
            }
        } catch (CopilotSdkInvocationException exception) {
            throw exception;
        } catch (Exception exception) {
            var rootCause = unwrapCompletionException(exception);
            log.error(
                    "Copilot SDK invocation failed runReference={} exceptionType={} rootCauseType={} rootCauseMessage={}",
                    runReference,
                    exception.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    exception
            );
            throw new CopilotSdkInvocationException(buildFailureMessage(rootCause), exception);
        } finally {
            metricsRecorder.recordExecutionDurations(
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

            metricsRecorder.recordAssistantUsage(
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

            metricsRecorder.recordSessionUsageInfo(
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
