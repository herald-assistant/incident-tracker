package pl.mkn.incidenttracker.analysis.ai.copilot.execution;

import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@UtilityClass
public class CopilotSessionEventLogger {

    public SessionLogSummary newSessionLogSummary(String correlationId) {
        return new SessionLogSummary(correlationId);
    }

    public void logSessionEvent(AbstractSessionEvent event, CopilotSession session, SessionLogSummary summary) {
        String sessionId = session.getSessionId();
        String correlationId = summary.correlationId();

        if (event instanceof ToolExecutionStartEvent toolExecutionStartEvent) {
            logToolExecutionStart(toolExecutionStartEvent, correlationId, sessionId, summary);
            return;
        }

        if (event instanceof ToolExecutionCompleteEvent toolExecutionCompleteEvent) {
            logToolExecutionComplete(toolExecutionCompleteEvent, correlationId, sessionId, summary);
            return;
        }

        if (event instanceof SessionErrorEvent sessionErrorEvent) {
            logSessionError(sessionErrorEvent, correlationId, sessionId);
            return;
        }

        if (event instanceof AssistantMessageEvent assistantMessageEvent) {
            logAssistantMessage(assistantMessageEvent, correlationId, sessionId);
            return;
        }

        log.debug(
                "Copilot session event correlationId={} sessionId={} type={} eventId={} parentId={} timestamp={}",
                correlationId,
                sessionId,
                event.getType(),
                event.getId(),
                event.getParentId(),
                event.getTimestamp()
        );
    }

    public void logSessionSummary(String sessionId, SessionLogSummary summary, long durationMs) {
        log.info(
                "Copilot session summary correlationId={} sessionId={} durationMs={} toolCalls={} toolFailures={} toolsUsed={}",
                summary.correlationId(),
                sessionId,
                durationMs,
                summary.toolCalls().get(),
                summary.toolFailures().get(),
                summary.toolsUsed()
        );
    }

    private void logToolExecutionStart(ToolExecutionStartEvent event, String correlationId, String sessionId, SessionLogSummary summary) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot tool execution started correlationId={} sessionId={} eventId={} type={}", correlationId, sessionId, event.getId(), event.getType());
            return;
        }

        summary.toolCalls().incrementAndGet();
        if (data.toolName() != null && !data.toolName().isBlank()) {
            summary.toolsUsed().add(data.toolName());
        }

        log.info(
                "Copilot tool execution started correlationId={} sessionId={} eventId={} toolCallId={} toolName={} mcpServerName={} mcpToolName={} parentToolCallId={} argumentsPreview={}",
                correlationId,
                sessionId,
                event.getId(),
                data.toolCallId(),
                data.toolName(),
                data.mcpServerName(),
                data.mcpToolName(),
                data.parentToolCallId(),
                abbreviate(String.valueOf(data.arguments()), 500)
        );
    }

    private void logToolExecutionComplete(ToolExecutionCompleteEvent event, String correlationId, String sessionId, SessionLogSummary summary) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot tool execution completed correlationId={} sessionId={} eventId={} type={}", correlationId, sessionId, event.getId(), event.getType());
            return;
        }

        if (!data.success()) {
            summary.toolFailures().incrementAndGet();
        }

        log.info(
                "Copilot tool execution completed correlationId={} sessionId={} eventId={} toolCallId={} success={} model={} interactionId={} parentToolCallId={} resultPreview={} errorPreview={}",
                correlationId,
                sessionId,
                event.getId(),
                data.toolCallId(),
                data.success(),
                data.model(),
                data.interactionId(),
                data.parentToolCallId(),
                abbreviate(String.valueOf(data.result()), 500),
                abbreviate(String.valueOf(data.error()), 300)
        );
    }

    private void logSessionError(SessionErrorEvent event, String correlationId, String sessionId) {
        var data = event.getData();
        if (data == null) {
            log.error("Copilot session error correlationId={} sessionId={} eventId={} type={}", correlationId, sessionId, event.getId(), event.getType());
            return;
        }

        log.error(
                "Copilot session error correlationId={} sessionId={} eventId={} errorType={} statusCode={} providerCallId={} message={} stackPreview={}",
                correlationId,
                sessionId,
                event.getId(),
                data.errorType(),
                data.statusCode(),
                data.providerCallId(),
                data.message(),
                abbreviate(data.stack(), 500)
        );
    }

    private void logAssistantMessage(AssistantMessageEvent event, String correlationId, String sessionId) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot assistant message correlationId={} sessionId={} eventId={} type={}", correlationId, sessionId, event.getId(), event.getType());
            return;
        }

        log.info(
                "Copilot assistant message correlationId={} sessionId={} eventId={} messageId={} interactionId={} toolRequestCount={} contentPreview={}",
                correlationId,
                sessionId,
                event.getId(),
                data.messageId(),
                data.interactionId(),
                data.toolRequests() != null ? data.toolRequests().size() : 0,
                abbreviate(data.content(), 500)
        );
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }

    public record SessionLogSummary(
            String correlationId,
            AtomicInteger toolCalls,
            AtomicInteger toolFailures,
            Set<String> toolsUsed
    ) {
        public SessionLogSummary(String correlationId) {
            this(correlationId, new AtomicInteger(), new AtomicInteger(), new LinkedHashSet<>());
        }
    }

}
