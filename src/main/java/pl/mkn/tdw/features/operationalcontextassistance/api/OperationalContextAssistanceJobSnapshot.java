package pl.mkn.tdw.features.operationalcontextassistance.api;

import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;

import java.time.Instant;
import java.util.List;

public record OperationalContextAssistanceJobSnapshot(
        String jobId,
        OperationalContextAssistanceJobStatus status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        AnalysisAiUsage usage,
        String catalogDigest,
        OperationalContextAssistanceSourceRevision sourceRevision,
        List<String> sourceRefs,
        List<String> visibilityLimits,
        OperationalContextAssistanceDraft draft,
        List<OperationalContextAssistanceProposalPreview> previews,
        List<OperationalContextAssistanceProposalDecision> proposalDecisions,
        String preparedPrompt,
        OperationalContextAssistanceReviewDraft reviewDraft
) {
    public OperationalContextAssistanceJobSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        previews = previews != null ? List.copyOf(previews) : List.of();
        proposalDecisions = proposalDecisions != null ? List.copyOf(proposalDecisions) : List.of();
    }
}
