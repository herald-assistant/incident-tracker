package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

import java.util.List;

public record CopilotInitialAnalysisRunAssembly(
        CopilotToolSessionContext toolSessionContext,
        List<CopilotArtifactService.Artifact> renderedArtifacts,
        String prompt,
        CopilotPreparedSessionRequest preparedSessionRequest
) {

    public CopilotInitialAnalysisRunAssembly {
        renderedArtifacts = renderedArtifacts != null ? List.copyOf(renderedArtifacts) : List.of();
    }
}
