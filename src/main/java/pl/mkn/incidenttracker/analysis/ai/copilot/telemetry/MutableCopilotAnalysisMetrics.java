package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Finding;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Report;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;

import java.util.ArrayList;
import java.util.List;

final class MutableCopilotAnalysisMetrics {

    private final String analysisRunId;
    private final String copilotSessionId;
    private final String correlationId;
    private int evidenceSectionCount;
    private int evidenceItemCount;
    private int artifactCount;
    private long artifactTotalCharacters;
    private long promptCharacters;
    private long preparationDurationMs;
    private long clientStartDurationMs;
    private long createSessionDurationMs;
    private long sendAndWaitDurationMs;
    private long totalExecutionDurationMs;
    private int totalToolCalls;
    private int elasticToolCalls;
    private int gitLabToolCalls;
    private int databaseToolCalls;
    private int gitLabReadFileCalls;
    private int gitLabReadChunkCalls;
    private long gitLabReturnedCharacters;
    private int databaseQueryCalls;
    private int databaseRawSqlCalls;
    private long databaseReturnedCharacters;
    private boolean structuredResponse;
    private boolean fallbackResponseUsed;
    private String detectedProblem;
    private String responseConfidence;
    private boolean qualityGateEnabled;
    private CopilotResponseQualityProperties.Mode qualityGateMode;
    private boolean qualityGatePassed = true;
    private List<Finding> qualityFindings = List.of();
    private int budgetSoftLimitExceededCount;
    private int budgetDeniedToolCalls;
    private int budgetRawSqlAttempts;
    private final ArrayList<String> budgetWarnings = new ArrayList<>();

    MutableCopilotAnalysisMetrics(
            String analysisRunId,
            String copilotSessionId,
            String correlationId
    ) {
        this.analysisRunId = analysisRunId;
        this.copilotSessionId = copilotSessionId;
        this.correlationId = correlationId;
    }

    synchronized void recordPreparation(CopilotArtifactMetrics metrics) {
        evidenceSectionCount = metrics.evidenceSectionCount();
        evidenceItemCount = metrics.evidenceItemCount();
        artifactCount = metrics.artifactCount();
        artifactTotalCharacters = metrics.artifactTotalCharacters();
        promptCharacters = metrics.promptCharacters();
        preparationDurationMs = metrics.preparationDurationMs();
    }

    synchronized void recordExecutionDurations(
            long clientStartDurationMs,
            long createSessionDurationMs,
            long sendAndWaitDurationMs,
            long totalExecutionDurationMs
    ) {
        this.clientStartDurationMs = clientStartDurationMs;
        this.createSessionDurationMs = createSessionDurationMs;
        this.sendAndWaitDurationMs = sendAndWaitDurationMs;
        this.totalExecutionDurationMs = totalExecutionDurationMs;
    }

    synchronized void recordToolCall(CopilotToolMetrics toolMetrics) {
        totalToolCalls++;
        switch (toolMetrics.toolGroup()) {
            case "elasticsearch" -> elasticToolCalls++;
            case "gitlab" -> {
                gitLabToolCalls++;
                gitLabReturnedCharacters += toolMetrics.rawResultCharacters();
            }
            case "database" -> {
                databaseToolCalls++;
                databaseReturnedCharacters += toolMetrics.rawResultCharacters();
            }
            default -> {
            }
        }

        if (toolMetrics.gitLabReadFileCall()) {
            gitLabReadFileCalls++;
        }
        if (toolMetrics.gitLabReadChunkCall()) {
            gitLabReadChunkCalls++;
        }
        if (toolMetrics.databaseQueryCall()) {
            databaseQueryCalls++;
        }
        if (toolMetrics.databaseRawSqlCall()) {
            databaseRawSqlCalls++;
        }
    }

    synchronized void recordResponse(
            boolean structuredResponse,
            boolean fallbackResponseUsed,
            String detectedProblem,
            String responseConfidence
    ) {
        this.structuredResponse = structuredResponse;
        this.fallbackResponseUsed = fallbackResponseUsed;
        this.detectedProblem = detectedProblem;
        this.responseConfidence = responseConfidence;
    }

    synchronized void recordQualityReport(Report report) {
        qualityGateEnabled = report.enabled();
        qualityGateMode = report.mode();
        qualityGatePassed = report.passed();
        qualityFindings = report.findings();
    }

    synchronized void recordBudgetWarnings(List<String> warnings) {
        budgetSoftLimitExceededCount += warnings.size();
        budgetWarnings.addAll(warnings);
    }

    synchronized void recordBudgetDenied(String toolName, String reason) {
        budgetDeniedToolCalls++;
        budgetWarnings.add("Denied tool `%s`: %s".formatted(toolName, reason));
    }

    synchronized void recordBudgetRawSqlAttempt() {
        budgetRawSqlAttempts++;
    }

    synchronized CopilotAnalysisMetrics snapshot() {
        return new CopilotAnalysisMetrics(
                analysisRunId,
                copilotSessionId,
                correlationId,
                evidenceSectionCount,
                evidenceItemCount,
                artifactCount,
                artifactTotalCharacters,
                promptCharacters,
                preparationDurationMs,
                clientStartDurationMs,
                createSessionDurationMs,
                sendAndWaitDurationMs,
                totalExecutionDurationMs,
                totalToolCalls,
                elasticToolCalls,
                gitLabToolCalls,
                databaseToolCalls,
                gitLabReadFileCalls,
                gitLabReadChunkCalls,
                gitLabReturnedCharacters,
                databaseQueryCalls,
                databaseRawSqlCalls,
                databaseReturnedCharacters,
                structuredResponse,
                fallbackResponseUsed,
                detectedProblem,
                responseConfidence,
                qualityGateEnabled,
                qualityGateMode,
                qualityGatePassed,
                qualityFindings.size(),
                qualityFindings,
                budgetSoftLimitExceededCount,
                budgetDeniedToolCalls,
                budgetRawSqlAttempts,
                budgetWarnings
        );
    }
}
