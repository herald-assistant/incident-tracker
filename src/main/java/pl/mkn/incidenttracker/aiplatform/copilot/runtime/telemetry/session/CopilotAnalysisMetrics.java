package pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiUsage;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityMode;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityReport.Finding;

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
        AnalysisAiUsage usage,
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
        CopilotResponseQualityMode qualityGateMode,
        boolean qualityGatePassed,
        int qualityFindingCount,
        List<Finding> qualityFindings,
        int budgetSoftLimitExceededCount,
        int budgetDeniedToolCalls,
        int budgetRawSqlAttempts,
        List<String> budgetWarnings
) {

    public CopilotAnalysisMetrics {
        qualityFindings = qualityFindings != null ? List.copyOf(qualityFindings) : List.of();
        budgetWarnings = budgetWarnings != null ? List.copyOf(budgetWarnings) : List.of();
    }
}
