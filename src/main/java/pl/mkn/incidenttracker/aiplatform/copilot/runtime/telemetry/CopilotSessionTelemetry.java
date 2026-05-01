package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry;

import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityReport;

import java.util.Optional;

public interface CopilotSessionTelemetry {

    void recordPreparation(CopilotSessionPreparationMetrics metrics);

    void recordResponse(
            String copilotSessionId,
            boolean structuredResponse,
            boolean fallbackResponseUsed,
            String detectedProblem,
            String responseConfidence
    );

    void recordQualityReport(
            String runReference,
            String copilotSessionId,
            CopilotResponseQualityReport report
    );

    Optional<CopilotSessionTelemetrySnapshot> completeSession(String copilotSessionId);

    void discardSession(String copilotSessionId);
}
