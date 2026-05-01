package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution.CopilotSessionExecutionMetricsRecorder;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality.CopilotResponseQualityReport;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionPreparationMetrics;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.telemetry.CopilotToolMetrics;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class CopilotSessionMetricsRegistry implements CopilotSessionExecutionMetricsRecorder {

    private final CopilotMetricsProperties properties;
    private final Map<String, MutableCopilotAnalysisMetrics> metricsBySessionId = new ConcurrentHashMap<>();

    public void recordPreparation(
            CopilotToolSessionContext context,
            InitialAnalysisRequest request,
            List<CopilotRenderedArtifact> artifacts,
            String prompt,
            long preparationDurationMs
    ) {
        recordPreparation(toPreparationMetrics(context, request, artifacts, prompt, preparationDurationMs));
    }

    public void recordPreparation(CopilotSessionPreparationMetrics preparationMetrics) {
        if (!enabled()
                || preparationMetrics == null
                || !StringUtils.hasText(preparationMetrics.copilotSessionId())) {
            return;
        }

        var metrics = metricsBySessionId.computeIfAbsent(
                preparationMetrics.copilotSessionId(),
                sessionId -> new MutableCopilotAnalysisMetrics(
                        preparationMetrics.analysisRunId(),
                        preparationMetrics.copilotSessionId(),
                        preparationMetrics.runReference()
                )
        );
        metrics.recordPreparation(toArtifactMetrics(preparationMetrics));
    }

    @Override
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

    @Override
    public void recordAssistantUsage(
            String copilotSessionId,
            String model,
            Double inputTokens,
            Double outputTokens,
            Double cacheReadTokens,
            Double cacheWriteTokens,
            Double cost,
            Double durationMs
    ) {
        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordAssistantUsage(
                model,
                inputTokens,
                outputTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cost,
                durationMs
        ));
    }

    @Override
    public void recordSessionUsageInfo(
            String copilotSessionId,
            double tokenLimit,
            double currentTokens,
            double messagesLength
    ) {
        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordSessionUsageInfo(
                tokenLimit,
                currentTokens,
                messagesLength
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

    public void recordQualityReport(String copilotSessionId, CopilotResponseQualityReport report) {
        if (report == null) {
            return;
        }

        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordQualityReport(report));
    }

    public void recordBudgetWarnings(String copilotSessionId, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }

        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordBudgetWarnings(warnings));
    }

    public void recordBudgetDenied(String copilotSessionId, String toolName, String reason) {
        withMetrics(copilotSessionId).ifPresent(metrics -> metrics.recordBudgetDenied(toolName, reason));
    }

    public void recordBudgetRawSqlAttempt(String copilotSessionId) {
        withMetrics(copilotSessionId).ifPresent(MutableCopilotAnalysisMetrics::recordBudgetRawSqlAttempt);
    }

    public Optional<CopilotAnalysisMetrics> snapshot(String copilotSessionId) {
        return withMetrics(copilotSessionId).map(MutableCopilotAnalysisMetrics::snapshot);
    }

    public Optional<CopilotAnalysisMetrics> remove(String copilotSessionId) {
        if (!enabled() || !StringUtils.hasText(copilotSessionId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(metricsBySessionId.remove(copilotSessionId))
                .map(MutableCopilotAnalysisMetrics::snapshot);
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

    private CopilotSessionPreparationMetrics toPreparationMetrics(
            CopilotToolSessionContext context,
            InitialAnalysisRequest request,
            List<CopilotRenderedArtifact> artifacts,
            String prompt,
            long preparationDurationMs
    ) {
        if (context == null) {
            return null;
        }

        var evidenceSections = evidenceSections(request);
        var safeArtifacts = artifacts != null ? artifacts : List.<CopilotRenderedArtifact>of();

        return new CopilotSessionPreparationMetrics(
                context.analysisRunId(),
                context.copilotSessionId(),
                context.correlationId(),
                evidenceSections.size(),
                evidenceSections.stream().mapToInt(section -> section.items().size()).sum(),
                safeArtifacts.size(),
                safeArtifacts.stream()
                        .mapToLong(artifact -> artifact.content() != null ? artifact.content().length() : 0L)
                        .sum(),
                prompt != null ? prompt.length() : 0L,
                preparationDurationMs
        );
    }

    private List<AnalysisEvidenceSection> evidenceSections(InitialAnalysisRequest request) {
        return request != null && request.evidenceSections() != null
                ? request.evidenceSections()
                : List.of();
    }

    private CopilotArtifactMetrics toArtifactMetrics(CopilotSessionPreparationMetrics metrics) {
        return new CopilotArtifactMetrics(
                metrics.analysisRunId(),
                metrics.copilotSessionId(),
                metrics.runReference(),
                metrics.evidenceSectionCount(),
                metrics.evidenceItemCount(),
                metrics.artifactCount(),
                metrics.artifactTotalCharacters(),
                metrics.promptCharacters(),
                metrics.preparationDurationMs()
        );
    }

    private boolean enabled() {
        return properties == null || properties.isEnabled();
    }
}
