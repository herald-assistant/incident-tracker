package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.Instant;
import java.util.List;

public record DeliveryScopeUnitResponse(
        String unitId,
        String status,
        List<DeliveryScopeIssueResponse> issues,
        List<DeliveryScopeMergeRequestResponse> mergeRequests,
        DeliveryScopeResponse assessment,
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

    public DeliveryScopeUnitResponse {
        issues = issues != null ? List.copyOf(issues) : List.of();
        mergeRequests = mergeRequests != null ? List.copyOf(mergeRequests) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
