package pl.mkn.incidenttracker.analysis.ai.copilot.response;

public record CopilotEvidenceReference(
        String field,
        String artifactId,
        String itemId,
        String claim
) {
}
