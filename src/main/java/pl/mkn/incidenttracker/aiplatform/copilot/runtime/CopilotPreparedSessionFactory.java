package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.MessageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CopilotPreparedSessionFactory {

    private final CopilotSessionConfigFactory sessionConfigFactory;

    public CopilotPreparedSession prepare(CopilotRunRequest request) {
        return new CopilotPreparedSession(
                request.runReference(),
                sessionConfigFactory.clientOptions(),
                sessionConfigFactory.sessionConfig(request.sessionConfigRequest()),
                new MessageOptions().setPrompt(request.prompt()),
                request.prompt(),
                request.artifactContents(),
                request.evidenceSink()
        );
    }
}
