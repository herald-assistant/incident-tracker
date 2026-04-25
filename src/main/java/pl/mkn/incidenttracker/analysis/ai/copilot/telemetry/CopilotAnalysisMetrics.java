package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

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
        boolean legacyParserUsed,
        boolean fallbackResponseUsed,
        String detectedProblem,
        String responseConfidence
) {
}
