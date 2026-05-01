package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

import java.util.List;
import java.util.Objects;

public record CopilotInitialAnalysisPreparationMetrics(
        CopilotToolSessionContext toolSessionContext,
        List<CopilotRenderedArtifact> renderedArtifacts
) {

    public CopilotInitialAnalysisPreparationMetrics {
        Objects.requireNonNull(toolSessionContext, "toolSessionContext must not be null");
        renderedArtifacts = renderedArtifacts != null ? List.copyOf(renderedArtifacts) : List.of();
    }
}
