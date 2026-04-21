package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

public record CopilotToolSessionContext(
        String analysisRunId,
        String copilotSessionId,
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup
) {
}
