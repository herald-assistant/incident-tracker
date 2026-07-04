package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotIncidentRunRequestFactory {

    private final CopilotArtifactContentMapper artifactContentMapper;
    private final CopilotRunAuthMapper runAuthMapper;

    public CopilotRunRequest create(
            String runReference,
            AnalysisAiAuthRef authRef,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return create(
                runReference,
                authRef,
                CopilotSessionTarget.newSession(),
                prompt,
                sessionConfigRequest,
                renderedArtifacts
        );
    }

    public CopilotRunRequest create(
            String runReference,
            AnalysisAiAuthRef authRef,
            CopilotSessionTarget sessionTarget,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            List<CopilotRenderedArtifact> renderedArtifacts
    ) {
        return new CopilotRunRequest(
                runReference,
                runAuthMapper.toRunAuth(authRef),
                sessionTarget,
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
