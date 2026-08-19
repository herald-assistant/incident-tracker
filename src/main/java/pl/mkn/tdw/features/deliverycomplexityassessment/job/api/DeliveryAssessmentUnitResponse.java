package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.Instant;
import java.util.List;

public record DeliveryAssessmentUnitResponse(
        String unitId,
        String status,
        List<DeliveryAssessmentIssueResponse> issues,
        List<DeliveryAssessmentMergeRequestResponse> mergeRequests,
        DeliveryAssessmentResponse assessment,
        List<String> visibilityLimits,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        String preparedPrompt,
        Instant promptPreparedAt,
        String rawAiResponse,
        AnalysisAiUsage usage
) {

    public DeliveryAssessmentUnitResponse {
        issues = issues != null ? List.copyOf(issues) : List.of();
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
