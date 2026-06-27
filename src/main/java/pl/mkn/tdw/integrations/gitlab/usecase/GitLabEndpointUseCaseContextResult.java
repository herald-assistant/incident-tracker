package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseContextResult(
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabEndpointUseCaseEndpointContext endpoint,
        List<GitLabEndpointUseCaseFileCandidate> files,
        List<GitLabEndpointUseCaseRelation> relations,
        List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
        List<String> limitations,
        List<String> suggestedNextReads,
        GitLabEndpointUseCaseLimits limits,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabEndpointUseCaseContextResult {
        files = GitLabEndpointUseCaseModelSupport.copy(files);
        relations = GitLabEndpointUseCaseModelSupport.copy(relations);
        unresolved = GitLabEndpointUseCaseModelSupport.copy(unresolved);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        suggestedNextReads = GitLabEndpointUseCaseModelSupport.copyStrings(suggestedNextReads);
        limits = limits != null ? limits : GitLabEndpointUseCaseLimits.defaults();
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
