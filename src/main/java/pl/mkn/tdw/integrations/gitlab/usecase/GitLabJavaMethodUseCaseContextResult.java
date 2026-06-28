package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMethodUseCaseContextResult(
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabJavaMethodUseCaseEntryMethod entryMethod,
        List<GitLabEndpointUseCaseFileCandidate> files,
        List<GitLabEndpointUseCaseRelation> relations,
        List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
        List<String> limitations,
        List<String> suggestedNextReads,
        GitLabJavaMethodUseCaseContextLimits limits,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMethodUseCaseContextResult {
        files = GitLabEndpointUseCaseModelSupport.copy(files);
        relations = GitLabEndpointUseCaseModelSupport.copy(relations);
        unresolved = GitLabEndpointUseCaseModelSupport.copy(unresolved);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        suggestedNextReads = GitLabEndpointUseCaseModelSupport.copyStrings(suggestedNextReads);
        limits = limits != null ? limits : GitLabJavaMethodUseCaseContextLimits.defaults();
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
