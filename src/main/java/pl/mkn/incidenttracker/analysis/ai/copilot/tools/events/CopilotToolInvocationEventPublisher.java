package pl.mkn.incidenttracker.analysis.ai.copilot.tools.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotToolInvocationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(CopilotToolInvocationEvent event) {
        if (event == null) {
            return;
        }

        try {
            applicationEventPublisher.publishEvent(event);
        } catch (RuntimeException exception) {
            log.warn(
                    "Copilot tool invocation event listener failed eventType={} sessionId={} toolCallId={} toolName={} reason={}",
                    event.getClass().getSimpleName(),
                    event.sessionId(),
                    event.toolCallId(),
                    event.toolName(),
                    exception.getMessage(),
                    exception
            );
        }
    }
}
