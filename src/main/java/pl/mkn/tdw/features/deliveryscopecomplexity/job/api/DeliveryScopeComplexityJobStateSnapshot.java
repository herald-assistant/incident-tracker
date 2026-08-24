package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DeliveryScopeComplexityJobStateSnapshot(
        String jobId,
        String jiraProject,
        LocalDate fromDate,
        LocalDate toDate,
        String aiModel,
        String reasoningEffort,
        String status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        int discoveredIssues,
        int processedIssues,
        int totalIssues,
        String effectiveJql,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        List<DeliveryScopeUnitResponse> units,
        DeliveryScopeAggregateResponse aggregate
) {

    public DeliveryScopeComplexityJobStateSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        units = units != null ? List.copyOf(units) : List.of();
    }
}
