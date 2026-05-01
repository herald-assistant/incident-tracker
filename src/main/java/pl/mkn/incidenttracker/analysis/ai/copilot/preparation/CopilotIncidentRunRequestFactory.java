package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotIncidentRunRequestFactory {

    private final CopilotArtifactContentMapper artifactContentMapper;

    public CopilotRunRequest create(
            String runReference,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return new CopilotRunRequest(
                runReference,
                prompt,
                sessionConfigRequest,
                artifactContentMapper.toArtifactContentMap(renderedArtifacts),
                null
        );
    }
}
