package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityFinding;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;

import java.util.List;

public record CopilotAnalysisMetrics(
        String analysisRunId,
        String copilotSessionId,
        String correlationId,
        int evidenceSectionCount,
        int evidenceItemCount,
        int artifactCount,
        long artifactTotalCharacters,
        long promptCharacters,
        long preparationDurationMs,
        long clientStartDurationMs,
        long createSessionDurationMs,
        long sendAndWaitDurationMs,
        long totalExecutionDurationMs,
        int totalToolCalls,
        int elasticToolCalls,
        int gitLabToolCalls,
        int databaseToolCalls,
        int gitLabReadFileCalls,
        int gitLabReadChunkCalls,
        long gitLabReturnedCharacters,
        int databaseQueryCalls,
        int databaseRawSqlCalls,
        long databaseReturnedCharacters,
        boolean structuredResponse,
        boolean fallbackResponseUsed,
        String detectedProblem,
        String responseConfidence,
        boolean qualityGateEnabled,
        CopilotResponseQualityProperties.Mode qualityGateMode,
        boolean qualityGatePassed,
        int qualityFindingCount,
        List<CopilotResponseQualityFinding> qualityFindings
) {

    public CopilotAnalysisMetrics {
        qualityFindings = qualityFindings != null ? List.copyOf(qualityFindings) : List.of();
    }
}
