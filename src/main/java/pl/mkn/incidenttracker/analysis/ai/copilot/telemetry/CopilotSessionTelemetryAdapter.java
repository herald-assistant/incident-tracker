package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityReport;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionPreparationMetrics;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionTelemetry;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionTelemetrySnapshot;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotUsage;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CopilotSessionTelemetryAdapter implements CopilotSessionTelemetry {

    private final CopilotSessionMetricsRegistry metricsRegistry;
    private final CopilotMetricsLogger metricsLogger;

    @Override
    public void recordPreparation(CopilotSessionPreparationMetrics metrics) {
        metricsRegistry.recordPreparation(metrics);
    }

    @Override
    public void recordResponse(
            String copilotSessionId,
            boolean structuredResponse,
            boolean fallbackResponseUsed,
            String detectedProblem,
            String responseConfidence
    ) {
        metricsRegistry.recordResponse(
                copilotSessionId,
                structuredResponse,
                fallbackResponseUsed,
                detectedProblem,
                responseConfidence
        );
    }

    @Override
    public void recordQualityReport(
            String runReference,
            String copilotSessionId,
            CopilotResponseQualityReport report
    ) {
        metricsRegistry.recordQualityReport(copilotSessionId, report);
        metricsLogger.logQualityReport(runReference, report);
    }

    @Override
    public Optional<CopilotSessionTelemetrySnapshot> completeSession(String copilotSessionId) {
        return metricsRegistry.remove(copilotSessionId)
                .map(metrics -> {
                    metricsLogger.logSummary(metrics);
                    return new CopilotSessionTelemetrySnapshot(toUsage(metrics.usage()));
                });
    }

    @Override
    public void discardSession(String copilotSessionId) {
        metricsRegistry.remove(copilotSessionId).ifPresent(metricsLogger::logSummary);
    }

    private CopilotUsage toUsage(AnalysisAiUsage usage) {
        if (usage == null) {
            return null;
        }

        return new CopilotUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.totalTokens(),
                usage.cost(),
                usage.apiDurationMs(),
                usage.apiCallCount(),
                usage.model(),
                usage.contextTokenLimit(),
                usage.contextCurrentTokens(),
                usage.contextMessages()
        );
    }
}
