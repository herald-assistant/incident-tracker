package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseContextResult(
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabEndpointUseCaseEndpointContext endpoint,
        GitLabEndpointUseCaseSummary useCaseSummary,
        GitLabEndpointUseCaseGraph graph,
        List<GitLabEndpointUseCaseClassItem> classList,
        List<GitLabEndpointUseCaseWarning> warnings,
        List<GitLabEndpointUseCaseEvidence> evidence,
        List<String> suggestedNextReads,
        GitLabEndpointUseCaseLimits limits,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabEndpointUseCaseContextResult {
        useCaseSummary = useCaseSummary != null ? useCaseSummary : GitLabEndpointUseCaseSummary.empty();
        graph = graph != null ? graph : GitLabEndpointUseCaseGraph.empty();
        classList = classList != null ? List.copyOf(classList) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
        suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
        limits = limits != null ? limits : GitLabEndpointUseCaseLimits.defaults();
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.MEDIUM;
    }
}
