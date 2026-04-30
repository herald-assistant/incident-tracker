package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

import java.util.List;

public record CopilotFollowUpRunAssembly(
        CopilotToolSessionContext toolSessionContext,
        List<CopilotArtifactService.Artifact> renderedArtifacts,
        String prompt,
        CopilotPreparedSessionRequest preparedSessionRequest
) {

    public CopilotFollowUpRunAssembly {
        renderedArtifacts = renderedArtifacts != null ? List.copyOf(renderedArtifacts) : List.of();
    }
}
