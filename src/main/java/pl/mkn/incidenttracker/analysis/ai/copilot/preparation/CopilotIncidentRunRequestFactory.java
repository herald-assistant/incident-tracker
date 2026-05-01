package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotIncidentRunRequestFactory {

    private final CopilotArtifactService artifactService;

    public CopilotRunRequest create(
            String runReference,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotArtifactService.Artifact> renderedArtifacts
    ) {
        return new CopilotRunRequest(
                runReference,
                prompt,
                sessionConfigRequest,
                artifactService.toArtifactContentMap(renderedArtifacts),
                null
        );
    }
}
