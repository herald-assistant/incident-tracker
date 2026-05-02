package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRef;

import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CopilotIncidentRunRequestFactory {

    private final CopilotArtifactContentMapper artifactContentMapper;
    private final CopilotRunAuthMapper runAuthMapper;

    public CopilotIncidentRunRequestFactory(CopilotArtifactContentMapper artifactContentMapper) {
        this(artifactContentMapper, new CopilotRunAuthMapper());
    }

    public CopilotRunRequest create(
            String runReference,
            AnalysisAiAuthRef authRef,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return new CopilotRunRequest(
                runReference,
                runAuthMapper.toRunAuth(authRef),
                prompt,
                sessionConfigRequest,
                artifactContentMapper.toArtifactContentMap(renderedArtifacts),
                null
        );
    }

    public CopilotRunRequest create(
            String runReference,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return create(runReference, AnalysisAiAuthRef.localToken(null), prompt, sessionConfigRequest, renderedArtifacts);
    }
}
