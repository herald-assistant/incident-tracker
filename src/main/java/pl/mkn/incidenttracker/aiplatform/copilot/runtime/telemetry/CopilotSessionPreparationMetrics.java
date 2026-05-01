package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry;

public record CopilotSessionPreparationMetrics(
        String analysisRunId,
        String copilotSessionId,
        String runReference,
        int evidenceSectionCount,
        int evidenceItemCount,
        int artifactCount,
        long artifactTotalCharacters,
        long promptCharacters,
        long preparationDurationMs
) {
}
