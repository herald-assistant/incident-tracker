package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.rpc.MessageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CopilotPreparedSessionFactory {

    private final CopilotSessionConfigFactory sessionConfigFactory;

    public CopilotPreparedSession prepare(CopilotRunRequest request) {
        return new CopilotPreparedSession(
                request.runReference(),
                request.sessionTarget(),
                sessionConfigFactory.clientOptions(request.auth()),
                sessionConfigFactory.sessionConfig(request.sessionConfigRequest()),
                sessionConfigFactory.resumeSessionConfig(request.sessionConfigRequest()),
                new MessageOptions().setPrompt(request.prompt()),
                request.prompt(),
                request.artifactContents(),
                request.evidenceSink(),
                request.activitySink()
        );
    }
}
