package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.MessageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CopilotPreparedSessionFactory {

    private final CopilotSessionConfigFactory sessionConfigFactory;

    public CopilotPreparedSession prepare(CopilotPreparedSessionRequest request) {
        return new CopilotPreparedSession(
                request.correlationId(),
                sessionConfigFactory.clientOptions(),
                sessionConfigFactory.sessionConfig(request.sessionConfigRequest()),
                new MessageOptions().setPrompt(request.prompt()),
                request.prompt(),
                request.artifactContents()
        );
    }
}
