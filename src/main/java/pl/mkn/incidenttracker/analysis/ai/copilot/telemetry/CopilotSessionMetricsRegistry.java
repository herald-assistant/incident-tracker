package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Report;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class CopilotSessionMetricsRegistry {

    private final CopilotMetricsProperties properties;
    private final Map<String, MutableCopilotAnalysisMetrics> metricsBySessionId = new ConcurrentHashMap<>();

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

    public void recordQualityReport(String copilotSessionId, Report report) {
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

    private CopilotArtifactMetrics toArtifactMetrics(
            CopilotToolSessionContext context,
            AnalysisAiAnalysisRequest request,
            List<CopilotArtifactService.Artifact> artifacts,
            String prompt,
            long preparationDurationMs
    ) {
        var evidenceSections = request != null
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
}
