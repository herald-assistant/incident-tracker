package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session;

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
