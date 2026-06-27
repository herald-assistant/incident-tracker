package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotClientLifecycleLogger.*;
import static pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSessionEventLogger.*;

@Service
@Slf4j
public class CopilotSdkExecutionGateway {

    private final CopilotSdkProperties properties;
    private final CopilotToolEvidenceSessionStore toolEvidenceSessionStore;
    private final CopilotToolBudgetRegistry toolBudgetRegistry;

    @Autowired
    public CopilotSdkExecutionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotToolBudgetRegistry toolBudgetRegistry
    ) {
        this.properties = properties;
        this.toolEvidenceSessionStore = toolEvidenceSessionStore;
        this.toolBudgetRegistry = toolBudgetRegistry;
    }

    public CopilotExecutionResult execute(CopilotPreparedSession preparedSession) {
        var overallStart = System.nanoTime();
        var runReference = preparedSession.runReference();
        var usageAccumulator = new CopilotUsageAccumulator();

        try {
            try (var client = new CopilotClient(preparedSession.clientOptions())) {
                client.onLifecycle(event -> logSession(event, runReference));
                logClientState("before-start", client.getState(), runReference);
                var clientStart = System.nanoTime();
                client.start().join();
                logClientState("after-start", client.getState(), runReference);
                logDuration("client-start", runReference, nanosToMillis(clientStart));

                try {
                    var createSessionStart = System.nanoTime();

                    try (var session = client.createSession(preparedSession.sessionConfig()).join()) {
                        logDuration("create-session", runReference, nanosToMillis(createSessionStart));
                        var sessionSummary = newSessionLogSummary(runReference);
                        var sessionId = resolvedSessionId(session, preparedSession);

                        toolEvidenceSessionStore.registerSession(
                                sessionId,
                                preparedSession.evidenceSink()
                        );
                        toolBudgetRegistry.registerSession(sessionId);

                        session.on(event -> handleSessionEvent(
                                event,
                                session,
                                sessionSummary,
                                usageAccumulator,
                                preparedSession.activitySink()
                        ));

                        try {
                            var sendAndWaitStart = System.nanoTime();
                            var timeoutMs = sendAndWaitTimeoutMs();
                            log.info(
                                    "Copilot sendAndWait configuration runReference={} timeoutMs={}",
                                    runReference,
                                    timeoutMs
                            );
                            var response = session.sendAndWait(preparedSession.messageOptions(), timeoutMs).join();

                            logDuration("send-and-wait", runReference, nanosToMillis(sendAndWaitStart));
                            var content = response.getData() != null ? response.getData().content() : null;
                            if (content == null || content.isBlank()) {
                                throw new CopilotSdkInvocationException("Copilot SDK returned an empty assistant response.");
                            }

                            logSessionSummary(sessionId, sessionSummary, nanosToMillis(overallStart));
                            return new CopilotExecutionResult(content, usageAccumulator.snapshot(), sessionId);
                        } finally {
                            toolEvidenceSessionStore.unregisterSession(sessionId);
                            toolBudgetRegistry.unregisterSession(sessionId).ifPresent(snapshot -> log.info(
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
        }
    }

    private String resolvedSessionId(CopilotSession session, CopilotPreparedSession preparedSession) {
        var sessionId = session.getSessionId();
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }

        return preparedSession.sessionConfig() != null ? preparedSession.sessionConfig().getSessionId() : null;
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
            SessionEvent event,
            CopilotSession session,
            SessionLogSummary sessionSummary,
            CopilotUsageAccumulator usageAccumulator,
            Consumer<AnalysisAiActivityEvent> activitySink
    ) {
        logSessionEvent(event, session, sessionSummary);
        recordUsageEvent(event, usageAccumulator);
        publishActivityEvent(event, activitySink);
    }

    private void recordUsageEvent(SessionEvent event, CopilotUsageAccumulator usageAccumulator) {
        if (event instanceof AssistantUsageEvent assistantUsageEvent) {
            var data = assistantUsageEvent.getData();
            if (data == null) {
                return;
            }

            usageAccumulator.recordAssistantUsage(
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

            usageAccumulator.recordSessionUsageInfo(
                    numeric(data.tokenLimit()),
                    numeric(data.currentTokens()),
                    numeric(data.messagesLength())
            );
        }
    }

    private void publishActivityEvent(
            SessionEvent event,
            Consumer<AnalysisAiActivityEvent> activitySink
    ) {
        if (activitySink == null) {
            return;
        }

        var activityEvent = toActivityEvent(event);
        if (activityEvent == null) {
            return;
        }

        try {
            activitySink.accept(activityEvent);
        } catch (RuntimeException exception) {
            log.warn(
                    "Copilot activity listener failed eventType={} eventId={} reason={}",
                    event.getType(),
                    event.getId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private AnalysisAiActivityEvent toActivityEvent(SessionEvent event) {
        var details = baseDetails(event);

        if (event instanceof AssistantTurnStartEvent assistantTurnStartEvent) {
            var data = assistantTurnStartEvent.getData();
            if (data == null) {
                return activity(event, "TURN", "STARTED", "Turn AI rozpoczęty", "Copilot rozpoczął kolejny turn.", null, null, null, null, details);
            }
            put(details, "turnId", data.turnId());
            put(details, "interactionId", data.interactionId());
            return activity(
                    event,
                    "TURN",
                    "STARTED",
                    "Turn AI rozpoczęty",
                    StringUtils.hasText(data.turnId())
                            ? "Copilot rozpoczął turn " + data.turnId() + "."
                            : "Copilot rozpoczął kolejny turn.",
                    data.turnId(),
                    data.interactionId(),
                    null,
                    null,
                    details
            );
        }

        if (event instanceof AssistantTurnEndEvent assistantTurnEndEvent) {
            var data = assistantTurnEndEvent.getData();
            if (data != null) {
                put(details, "turnId", data.turnId());
            }
            return activity(
                    event,
                    "TURN",
                    "COMPLETED",
                    "Turn AI zakończony",
                    data != null && StringUtils.hasText(data.turnId())
                            ? "Copilot zakończył turn " + data.turnId() + "."
                            : "Copilot zakończył turn.",
                    data != null ? data.turnId() : null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof AssistantReasoningEvent assistantReasoningEvent) {
            var data = assistantReasoningEvent.getData();
            if (data == null) {
                return activity(event, "MESSAGE", "INFO", "Rozumowanie AI", "Copilot doprecyzował tok analizy.", null, null, null, null, details);
            }
            put(details, "reasoningId", data.reasoningId());
            put(details, "contentPreview", abbreviate(data.content(), 1_200));
            put(details, "contentLength", data.content() != null ? data.content().length() : 0);
            return activity(
                    event,
                    "MESSAGE",
                    "INFO",
                    "Rozumowanie AI",
                    firstSentence(data.content(), "Copilot doprecyzował tok analizy."),
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof AssistantMessageEvent assistantMessageEvent) {
            var data = assistantMessageEvent.getData();
            if (data == null) {
                return activity(event, "MESSAGE", "COMPLETED", "Wiadomość AI", "Copilot zwrócił wiadomość.", null, null, null, null, details);
            }
            var toolRequestCount = data.toolRequests() != null ? data.toolRequests().size() : 0;
            put(details, "messageId", data.messageId());
            put(details, "interactionId", data.interactionId());
            put(details, "parentToolCallId", data.parentToolCallId());
            put(details, "contentPreview", abbreviate(data.content(), 1_200));
            put(details, "contentLength", data.content() != null ? data.content().length() : 0);
            put(details, "toolRequestCount", toolRequestCount);
            put(details, "toolRequests", data.toolRequests());
            put(details, "reasoningTextPreview", abbreviate(data.reasoningText(), 1_200));
            put(details, "reasoningTextLength", data.reasoningText() != null ? data.reasoningText().length() : 0);
            put(details, "hasReasoningText", StringUtils.hasText(data.reasoningText()));
            put(details, "hasReasoningOpaque", StringUtils.hasText(data.reasoningOpaque()));
            put(details, "hasEncryptedContent", StringUtils.hasText(data.encryptedContent()));
            return activity(
                    event,
                    "MESSAGE",
                    "COMPLETED",
                    "Wiadomość AI",
                    assistantMessageSummary(data.content(), data.reasoningText(), toolRequestCount),
                    null,
                    data.interactionId(),
                    null,
                    null,
                    details
            );
        }

        if (event instanceof UserMessageEvent userMessageEvent) {
            var data = userMessageEvent.getData();
            if (data == null) {
                return activity(event, "MESSAGE", "STARTED", "Input do Copilota", "Aplikacja wysłała wiadomość do sesji.", null, null, null, null, details);
            }
            put(details, "source", data.source());
            put(details, "contentPreview", abbreviate(data.content(), 1_200));
            put(details, "contentLength", data.content() != null ? data.content().length() : 0);
            put(details, "transformedContentPreview", abbreviate(data.transformedContent(), 1_200));
            put(details, "transformedContentLength", data.transformedContent() != null ? data.transformedContent().length() : 0);
            put(details, "attachmentCount", data.attachments() != null ? data.attachments().size() : 0);
            put(details, "attachments", data.attachments());
            return activity(
                    event,
                    "MESSAGE",
                    "STARTED",
                    "Input do Copilota",
                    "Aplikacja wysłała prompt do sesji Copilota.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof ToolExecutionStartEvent toolExecutionStartEvent) {
            var data = toolExecutionStartEvent.getData();
            if (data == null) {
                return activity(event, "TOOL", "STARTED", "Tool uruchomiony", "Copilot uruchomił narzędzie.", null, null, null, null, details);
            }
            put(details, "toolCallId", data.toolCallId());
            put(details, "toolName", data.toolName());
            put(details, "arguments", data.arguments());
            put(details, "mcpServerName", data.mcpServerName());
            put(details, "mcpToolName", data.mcpToolName());
            put(details, "parentToolCallId", data.parentToolCallId());
            return activity(
                    event,
                    "TOOL",
                    "STARTED",
                    "Tool start: " + toolDisplayName(data.toolName()),
                    "Copilot uruchamia " + toolDisplayName(data.toolName()) + ".",
                    null,
                    null,
                    data.toolCallId(),
                    data.toolName(),
                    details
            );
        }

        if (event instanceof ToolExecutionProgressEvent toolExecutionProgressEvent) {
            var data = toolExecutionProgressEvent.getData();
            if (data != null) {
                put(details, "toolCallId", data.toolCallId());
                put(details, "progressMessage", data.progressMessage());
            }
            return activity(
                    event,
                    "TOOL",
                    "PROGRESS",
                    "Postęp toola",
                    data != null && StringUtils.hasText(data.progressMessage())
                            ? data.progressMessage()
                            : "Tool raportuje postęp wykonania.",
                    null,
                    null,
                    data != null ? data.toolCallId() : null,
                    null,
                    details
            );
        }

        if (event instanceof ToolExecutionPartialResultEvent toolExecutionPartialResultEvent) {
            var data = toolExecutionPartialResultEvent.getData();
            if (data != null) {
                put(details, "toolCallId", data.toolCallId());
                put(details, "partialOutputPreview", abbreviate(data.partialOutput(), 1_200));
                put(details, "partialOutputLength", data.partialOutput() != null ? data.partialOutput().length() : 0);
            }
            return activity(
                    event,
                    "TOOL",
                    "PROGRESS",
                    "Częściowy wynik toola",
                    "Tool zwrócił częściowy wynik wykonania.",
                    null,
                    null,
                    data != null ? data.toolCallId() : null,
                    null,
                    details
            );
        }

        if (event instanceof ToolExecutionCompleteEvent toolExecutionCompleteEvent) {
            var data = toolExecutionCompleteEvent.getData();
            if (data == null) {
                return activity(event, "TOOL", "COMPLETED", "Tool zakończony", "Tool zakończył wykonanie.", null, null, null, null, details);
            }
            put(details, "toolCallId", data.toolCallId());
            put(details, "success", data.success());
            put(details, "model", data.model());
            put(details, "interactionId", data.interactionId());
            put(details, "isUserRequested", data.isUserRequested());
            put(details, "parentToolCallId", data.parentToolCallId());
            put(details, "resultContentPreview", data.result() != null ? abbreviate(data.result().content(), 1_200) : null);
            put(details, "resultDetailedContentPreview", data.result() != null ? abbreviate(data.result().detailedContent(), 1_200) : null);
            put(details, "error", data.error());
            put(details, "toolTelemetry", data.toolTelemetry());
            return activity(
                    event,
                    "TOOL",
                    data.success() ? "COMPLETED" : "FAILED",
                    "Tool koniec",
                    data.success()
                            ? "Tool zakończył wykonanie poprawnie."
                            : "Tool zakończył wykonanie błędem.",
                    null,
                    data.interactionId(),
                    data.toolCallId(),
                    null,
                    details
            );
        }

        if (event instanceof AssistantUsageEvent assistantUsageEvent) {
            var data = assistantUsageEvent.getData();
            if (data == null) {
                return activity(event, "USAGE", "INFO", "Zużycie modelu", "Copilot zgłosił zużycie tokenów.", null, null, null, null, details);
            }
            put(details, "model", data.model());
            put(details, "inputTokens", data.inputTokens());
            put(details, "outputTokens", data.outputTokens());
            put(details, "cacheReadTokens", data.cacheReadTokens());
            put(details, "cacheWriteTokens", data.cacheWriteTokens());
            put(details, "cost", data.cost());
            put(details, "durationMs", data.duration());
            put(details, "initiator", data.initiator());
            put(details, "apiCallId", data.apiCallId());
            put(details, "providerCallId", data.providerCallId());
            put(details, "parentToolCallId", data.parentToolCallId());
            put(details, "quotaSnapshots", data.quotaSnapshots());
            put(details, "copilotUsage", data.copilotUsage());
            return activity(
                    event,
                    "USAGE",
                    "INFO",
                    "Zużycie modelu",
                    usageSummary(data),
                    null,
                    null,
                    data.parentToolCallId(),
                    null,
                    details
            );
        }

        if (event instanceof SessionUsageInfoEvent sessionUsageInfoEvent) {
            var data = sessionUsageInfoEvent.getData();
            if (data == null) {
                return activity(event, "CONTEXT", "INFO", "Kontekst sesji", "Copilot zgłosił aktualny rozmiar kontekstu.", null, null, null, null, details);
            }
            put(details, "tokenLimit", data.tokenLimit());
            put(details, "currentTokens", data.currentTokens());
            put(details, "messagesLength", data.messagesLength());
            return activity(
                    event,
                    "CONTEXT",
                    "INFO",
                    "Kontekst sesji",
                    "Kontekst: " + Math.round(numeric(data.currentTokens())) + "/" + Math.round(numeric(data.tokenLimit()))
                            + " tokenów, " + Math.round(numeric(data.messagesLength())) + " wiadomości.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof SessionCompactionCompleteEvent sessionCompactionCompleteEvent) {
            var data = sessionCompactionCompleteEvent.getData();
            if (data == null) {
                return activity(event, "CONTEXT", "COMPLETED", "Kompakcja kontekstu", "Copilot zakończył kompaktowanie kontekstu.", null, null, null, null, details);
            }
            put(details, "success", data.success());
            put(details, "error", data.error());
            put(details, "preCompactionTokens", data.preCompactionTokens());
            put(details, "postCompactionTokens", data.postCompactionTokens());
            put(details, "preCompactionMessagesLength", data.preCompactionMessagesLength());
            put(details, "messagesRemoved", data.messagesRemoved());
            put(details, "tokensRemoved", data.tokensRemoved());
            put(details, "summaryContentPreview", abbreviate(data.summaryContent(), 1_200));
            put(details, "checkpointNumber", data.checkpointNumber());
            put(details, "requestId", data.requestId());
            put(details, "compactionTokensUsed", data.compactionTokensUsed());
            return activity(
                    event,
                    "CONTEXT",
                    data.success() ? "COMPLETED" : "FAILED",
                    "Kompakcja kontekstu",
                    data.success()
                            ? "Copilot skompaktował kontekst sesji."
                            : "Kompakcja kontekstu zakończyła się błędem.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof SessionTruncationEvent sessionTruncationEvent) {
            var data = sessionTruncationEvent.getData();
            if (data == null) {
                return activity(event, "CONTEXT", "INFO", "Przycięcie kontekstu", "Copilot przyciął kontekst sesji.", null, null, null, null, details);
            }
            put(details, "tokenLimit", data.tokenLimit());
            put(details, "preTruncationTokensInMessages", data.preTruncationTokensInMessages());
            put(details, "postTruncationTokensInMessages", data.postTruncationTokensInMessages());
            put(details, "preTruncationMessagesLength", data.preTruncationMessagesLength());
            put(details, "postTruncationMessagesLength", data.postTruncationMessagesLength());
            put(details, "tokensRemovedDuringTruncation", data.tokensRemovedDuringTruncation());
            put(details, "messagesRemovedDuringTruncation", data.messagesRemovedDuringTruncation());
            put(details, "performedBy", data.performedBy());
            return activity(
                    event,
                    "CONTEXT",
                    "INFO",
                    "Przycięcie kontekstu",
                    "Copilot usunął " + Math.round(data.tokensRemovedDuringTruncation()) + " tokenów z kontekstu.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof SessionContextChangedEvent sessionContextChangedEvent) {
            put(details, "sessionContext", sessionContextChangedEvent.getData());
            return activity(
                    event,
                    "CONTEXT",
                    "INFO",
                    "Zmiana kontekstu sesji",
                    "Copilot zaktualizował kontekst sesji.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        if (event instanceof SessionErrorEvent sessionErrorEvent) {
            var data = sessionErrorEvent.getData();
            if (data != null) {
                put(details, "errorType", data.errorType());
                put(details, "message", data.message());
                put(details, "statusCode", data.statusCode());
                put(details, "providerCallId", data.providerCallId());
                put(details, "stackPreview", abbreviate(data.stack(), 1_200));
            }
            return activity(
                    event,
                    "ERROR",
                    "FAILED",
                    "Błąd sesji Copilota",
                    data != null && StringUtils.hasText(data.message())
                            ? data.message()
                            : "Copilot zgłosił błąd sesji.",
                    null,
                    null,
                    null,
                    null,
                    details
            );
        }

        return null;
    }

    private Map<String, Object> baseDetails(SessionEvent event) {
        var details = new LinkedHashMap<String, Object>();
        put(details, "type", event.getType());
        put(details, "eventId", event.getId() != null ? event.getId().toString() : null);
        put(details, "parentEventId", event.getParentId() != null ? event.getParentId().toString() : null);
        put(details, "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);
        put(details, "ephemeral", event.getEphemeral());
        return details;
    }

    private AnalysisAiActivityEvent activity(
            SessionEvent event,
            String category,
            String status,
            String title,
            String summary,
            String turnId,
            String interactionId,
            String toolCallId,
            String toolName,
            Map<String, Object> details
    ) {
        return new AnalysisAiActivityEvent(
                event.getId() != null ? event.getId().toString() : null,
                event.getParentId() != null ? event.getParentId().toString() : null,
                event.getType(),
                category,
                status,
                title,
                summary,
                turnId,
                interactionId,
                toolCallId,
                toolName,
                event.getTimestamp() != null ? event.getTimestamp().toInstant() : Instant.now(),
                details
        );
    }

    private void put(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private String usageSummary(AssistantUsageEvent.AssistantUsageEventData data) {
        var inputTokens = data.inputTokens() != null ? Math.round(data.inputTokens()) : 0L;
        var cacheReadTokens = data.cacheReadTokens() != null ? Math.round(data.cacheReadTokens()) : 0L;
        var outputTokens = data.outputTokens() != null ? Math.round(data.outputTokens()) : 0L;
        return "Model: " + toolDisplayName(data.model())
                + ", input " + inputTokens
                + ", cache " + cacheReadTokens
                + ", output " + outputTokens + " tokenów.";
    }

    private String assistantMessageSummary(String content, String reasoningText, int toolRequestCount) {
        if (StringUtils.hasText(content)) {
            return firstSentence(content, "Copilot zwrócił wiadomość.");
        }

        if (StringUtils.hasText(reasoningText)) {
            return firstSentence(reasoningText, "Copilot uzasadnił kolejny krok analizy.");
        }

        if (toolRequestCount > 0) {
            return "Copilot poprosił o " + toolRequestCount + " wywołań narzędzi.";
        }

        return "Copilot zwrócił wiadomość bez kolejnego tool calla.";
    }

    private String firstSentence(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        var normalized = value.trim().replaceAll("\\s+", " ");
        var dotIndex = normalized.indexOf('.');
        var sentence = dotIndex > 0 ? normalized.substring(0, dotIndex + 1) : normalized;
        return abbreviate(sentence, 220);
    }

    private String toolDisplayName(String value) {
        return StringUtils.hasText(value) ? value.trim() : "n/a";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }

    private double numeric(Number value) {
        return value != null ? value.doubleValue() : 0D;
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
