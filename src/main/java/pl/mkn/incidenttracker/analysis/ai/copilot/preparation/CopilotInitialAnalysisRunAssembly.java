package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRunRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

import java.util.List;

public record CopilotInitialAnalysisRunAssembly(
        CopilotToolSessionContext toolSessionContext,
        List<CopilotArtifactService.Artifact> renderedArtifacts,
        String prompt,
        CopilotRunRequest runRequest
) {

    public CopilotInitialAnalysisRunAssembly {
        renderedArtifacts = renderedArtifacts != null ? List.copyOf(renderedArtifacts) : List.of();
    }
}
