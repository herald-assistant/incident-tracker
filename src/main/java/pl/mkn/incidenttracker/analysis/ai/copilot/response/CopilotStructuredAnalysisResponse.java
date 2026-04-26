package pl.mkn.incidenttracker.analysis.ai.copilot.response;

import java.util.List;

public record CopilotStructuredAnalysisResponse(
        String detectedProblem,
        String summary,
        String recommendedAction,
        String rationale,
        String affectedFunction,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam,
        String confidence,
        List<CopilotEvidenceReference> evidenceReferences,
        List<String> visibilityLimits
) {

    public CopilotStructuredAnalysisResponse {
        evidenceReferences = evidenceReferences != null ? List.copyOf(evidenceReferences) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
