package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CopilotSessionMetricsRegistry {

    private final CopilotMetricsProperties properties;
    private final Map<String, MutableCopilotAnalysisMetrics> metricsBySessionId = new ConcurrentHashMap<>();

    public CopilotSessionMetricsRegistry(CopilotMetricsProperties properties) {
        this.properties = properties;
    }

    public void recordPreparation(
            CopilotToolSessionContext context,
            AnalysisAiAnalysisRequest request,
            List<CopilotArtifactService.Artifact> artifacts,
            String prompt,
            long preparationDurationMs
    ) {
        if (!enabled() || context == null || !StringUtils.hasText(context.copilotSessionId())) {
            return;
        }

        var metrics = metricsBySessionId.computeIfAbsent(
                context.copilotSessionId(),
                sessionId -> new MutableCopilotAnalysisMetrics(
                        context.analysisRunId(),
                        context.copilotSessionId(),
                        context.correlationId()
                )
        );
        metrics.recordPreparation(toArtifactMetrics(context, request, artifacts, prompt, preparationDurationMs));
    }

    public void recordExecutionDurations(
            String copilotSessionId,
            long clientStartDurationMs,
            long createSessionDurationMs,
            long sendAndWaitDurationMs,
            long totalExecutionDurationMs
    ) {
        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordExecutionDurations(
                clientStartDurationMs,
                createSessionDurationMs,
                sendAndWaitDurationMs,
                totalExecutionDurationMs
        ));
    }

    public void recordToolCall(CopilotToolMetrics toolMetrics) {
        if (toolMetrics == null) {
            return;
        }

        withMetrics(toolMetrics.copilotSessionId()).ifPresent(metrics -> metrics.recordToolCall(toolMetrics));
    }

    public void recordResponse(
            String copilotSessionId,
            boolean structuredResponse,
            boolean fallbackResponseUsed,
            String detectedProblem,
            String responseConfidence
    ) {
        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordResponse(
                structuredResponse,
                fallbackResponseUsed,
                detectedProblem,
                responseConfidence
        ));
    }

    public Optional<CopilotAnalysisMetrics> snapshot(String copilotSessionId) {
        return withMetrics(copilotSessionId).map(MutableCopilotAnalysisMetrics::snapshot);
    }

    public Optional<CopilotAnalysisMetrics> remove(String copilotSessionId) {
        if (!enabled() || !StringUtils.hasText(copilotSessionId)) {
            return Optional.empty();
        }

        var metrics = metricsBySessionId.remove(copilotSessionId);
        return metrics != null ? Optional.of(metrics.snapshot()) : Optional.empty();
    }

    public int sessionCount() {
        return metricsBySessionId.size();
    }

    private Optional<MutableCopilotAnalysisMetrics> withMetrics(String copilotSessionId) {
        if (!enabled() || !StringUtils.hasText(copilotSessionId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(metricsBySessionId.get(copilotSessionId));
    }

    private CopilotArtifactMetrics toArtifactMetrics(
            CopilotToolSessionContext context,
            AnalysisAiAnalysisRequest request,
            List<CopilotArtifactService.Artifact> artifacts,
            String prompt,
            long preparationDurationMs
    ) {
        var evidenceSections = request != null && request.evidenceSections() != null
                ? request.evidenceSections()
                : List.<pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection>of();
        var safeArtifacts = artifacts != null ? artifacts : List.<CopilotArtifactService.Artifact>of();

        return new CopilotArtifactMetrics(
                context.analysisRunId(),
                context.copilotSessionId(),
                context.correlationId(),
                evidenceSections.size(),
                evidenceSections.stream().mapToInt(section -> section.items().size()).sum(),
                safeArtifacts.size(),
                safeArtifacts.stream().mapToLong(artifact -> artifact.content() != null ? artifact.content().length() : 0L).sum(),
                prompt != null ? prompt.length() : 0L,
                preparationDurationMs
        );
    }

    private boolean enabled() {
        return properties == null || properties.isEnabled();
    }

    private static final class MutableCopilotAnalysisMetrics {

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

        private MutableCopilotAnalysisMetrics(
                String analysisRunId,
                String copilotSessionId,
                String correlationId
        ) {
            this.analysisRunId = analysisRunId;
            this.copilotSessionId = copilotSessionId;
            this.correlationId = correlationId;
        }

        private synchronized void recordPreparation(CopilotArtifactMetrics metrics) {
            evidenceSectionCount = metrics.evidenceSectionCount();
            evidenceItemCount = metrics.evidenceItemCount();
            artifactCount = metrics.artifactCount();
            artifactTotalCharacters = metrics.artifactTotalCharacters();
            promptCharacters = metrics.promptCharacters();
            preparationDurationMs = metrics.preparationDurationMs();
        }

        private synchronized void recordExecutionDurations(
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

        private synchronized void recordToolCall(CopilotToolMetrics toolMetrics) {
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

        private synchronized void recordResponse(
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

        private synchronized CopilotAnalysisMetrics snapshot() {
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
                    responseConfidence
            );
        }
    }
}
