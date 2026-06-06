package pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@UtilityClass
public class CopilotSessionEventLogger {

    public SessionLogSummary newSessionLogSummary(String runReference) {
        return new SessionLogSummary(runReference);
    }

    public void logSessionEvent(SessionEvent event, CopilotSession session, SessionLogSummary summary) {
        String sessionId = session.getSessionId();
        String runReference = summary.runReference();

        if (event instanceof ToolExecutionStartEvent toolExecutionStartEvent) {
            logToolExecutionStart(toolExecutionStartEvent, runReference, sessionId, summary);
            return;
        }

        if (event instanceof ToolExecutionCompleteEvent toolExecutionCompleteEvent) {
            logToolExecutionComplete(toolExecutionCompleteEvent, runReference, sessionId, summary);
            return;
        }

        if (event instanceof SessionErrorEvent sessionErrorEvent) {
            logSessionError(sessionErrorEvent, runReference, sessionId);
            return;
        }

        if (event instanceof AssistantMessageEvent assistantMessageEvent) {
            logAssistantMessage(assistantMessageEvent, runReference, sessionId);
            return;
        }

        log.debug(
                "Copilot session event runReference={} sessionId={} type={} eventId={} parentId={} timestamp={}",
                runReference,
                sessionId,
                event.getType(),
                event.getId(),
                event.getParentId(),
                event.getTimestamp()
        );
    }

    public void logSessionSummary(String sessionId, SessionLogSummary summary, long durationMs) {
        log.info(
                "Copilot session summary runReference={} sessionId={} durationMs={} toolCalls={} toolFailures={} toolsUsed={}",
                summary.runReference(),
                sessionId,
                durationMs,
                summary.toolCalls().get(),
                summary.toolFailures().get(),
                summary.toolsUsed()
        );
    }

    private void logToolExecutionStart(ToolExecutionStartEvent event, String runReference, String sessionId, SessionLogSummary summary) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot tool execution started runReference={} sessionId={} eventId={} type={}", runReference, sessionId, event.getId(), event.getType());
            return;
        }

        summary.toolCalls().incrementAndGet();
        if (data.toolName() != null && !data.toolName().isBlank()) {
            summary.toolsUsed().add(data.toolName());
        }

        log.info(
                "Copilot tool execution started runReference={} sessionId={} eventId={} toolCallId={} toolName={} mcpServerName={} mcpToolName={} parentToolCallId={} argumentsPreview={}",
                runReference,
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

    private void logToolExecutionComplete(ToolExecutionCompleteEvent event, String runReference, String sessionId, SessionLogSummary summary) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot tool execution completed runReference={} sessionId={} eventId={} type={}", runReference, sessionId, event.getId(), event.getType());
            return;
        }

        if (!data.success()) {
            summary.toolFailures().incrementAndGet();
        }

        log.info(
                "Copilot tool execution completed runReference={} sessionId={} eventId={} toolCallId={} success={} model={} interactionId={} parentToolCallId={} resultPreview={} errorPreview={}",
                runReference,
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

    private void logSessionError(SessionErrorEvent event, String runReference, String sessionId) {
        var data = event.getData();
        if (data == null) {
            log.error("Copilot session error runReference={} sessionId={} eventId={} type={}", runReference, sessionId, event.getId(), event.getType());
            return;
        }

        log.error(
                "Copilot session error runReference={} sessionId={} eventId={} errorType={} statusCode={} providerCallId={} message={} stackPreview={}",
                runReference,
                sessionId,
                event.getId(),
                data.errorType(),
                data.statusCode(),
                data.providerCallId(),
                data.message(),
                abbreviate(data.stack(), 500)
        );
    }

    private void logAssistantMessage(AssistantMessageEvent event, String runReference, String sessionId) {
        var data = event.getData();
        if (data == null) {
            log.info("Copilot assistant message runReference={} sessionId={} eventId={} type={}", runReference, sessionId, event.getId(), event.getType());
            return;
        }

        log.info(
                "Copilot assistant message runReference={} sessionId={} eventId={} messageId={} interactionId={} toolRequestCount={} contentPreview={}",
                runReference,
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
            String runReference,
            AtomicInteger toolCalls,
            AtomicInteger toolFailures,
            Set<String> toolsUsed
    ) {
        public SessionLogSummary(String runReference) {
            this(runReference, new AtomicInteger(), new AtomicInteger(), new LinkedHashSet<>());
        }
    }

}
