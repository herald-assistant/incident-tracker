package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

public record CopilotArtifactMetrics(
        String analysisRunId,
        String copilotSessionId,
        String correlationId,
        int evidenceSectionCount,
        int evidenceItemCount,
        int artifactCount,
        long artifactTotalCharacters,
        long promptCharacters,
        long preparationDurationMs
) {
}
