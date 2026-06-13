package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseCompressedContext(
        GitLabEndpointUseCaseSummary useCaseSummary,
        GitLabEndpointUseCaseGraph graph,
        List<GitLabEndpointUseCaseClassItem> classList,
        List<GitLabEndpointUseCaseEvidence> evidence,
        List<String> suggestedNextReads,
        GitLabEndpointUseCaseConfidence confidence
) {
    GitLabEndpointUseCaseCompressedContext {
        useCaseSummary = useCaseSummary != null ? useCaseSummary : GitLabEndpointUseCaseSummary.empty();
        graph = graph != null ? graph : GitLabEndpointUseCaseGraph.empty();
        classList = classList != null ? List.copyOf(classList) : List.of();
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
        suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.MEDIUM;
    }
}
