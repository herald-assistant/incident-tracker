package pl.mkn.tdw.aiplatform.copilot.tools.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationStartedEvent;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotToolInvocationLoggingListener {

    private final ObjectMapper objectMapper;

    @EventListener
    public void onToolInvocationStarted(CopilotToolInvocationStartedEvent event) {
        var expectedSessionId = event.sessionContext() != null
                ? event.sessionContext().copilotSessionId()
                : null;
        log.info(
                "Copilot tool invocation request expectedSessionId={} actualSessionId={} toolCallId={} toolName={} arguments={}",
                expectedSessionId,
                event.sessionId(),
                event.toolCallId(),
                event.toolName(),
                abbreviate(event.rawArguments(), 500)
        );
    }

    @EventListener
    public void onToolInvocationFinished(CopilotToolInvocationFinishedEvent event) {
        if (event.outcome() == CopilotToolInvocationOutcome.COMPLETED) {
            log.info(
                    "Copilot tool invocation result sessionId={} toolCallId={} toolName={} rawResultLength={} resultPreview={}",
                    event.sessionId(),
                    event.toolCallId(),
                    event.toolName(),
                    event.rawResult() != null ? event.rawResult().length() : 0,
                    abbreviate(serializeResultPreview(parseToolResult(event.rawResult())), 500)
            );
            return;
        }

        if (event.outcome() == CopilotToolInvocationOutcome.REJECTED) {
            log.info(
                    "Copilot tool invocation rejected sessionId={} toolCallId={} toolName={} resultPreview={}",
                    event.sessionId(),
                    event.toolCallId(),
                    event.toolName(),
                    abbreviate(serializeResultPreview(parseToolResult(event.rawResult())), 500)
            );
            return;
        }

        var exception = event.exception();
        log.error(
                "Copilot tool invocation failed sessionId={} toolCallId={} toolName={} error={} resultPreview={}",
                event.sessionId(),
                event.toolCallId(),
                event.toolName(),
                exception != null ? exception.getMessage() : null,
                abbreviate(serializeResultPreview(parseToolResult(event.rawResult())), 500),
                exception
        );
    }

    private Object parseToolResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return Map.of("status", "empty");
        }

        try {
            return objectMapper.readValue(rawResult, Object.class);
        }
        catch (JsonProcessingException exception) {
            return rawResult;
        }
    }

    private String serializeResultPreview(Object parsedResult) {
        try {
            return objectMapper.writeValueAsString(parsedResult);
        }
        catch (JsonProcessingException exception) {
            return String.valueOf(parsedResult);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        return value.length() > maxLength
                ? value.substring(0, maxLength) + "...(" + value.length() + " chars)"
                : value;
    }
}
