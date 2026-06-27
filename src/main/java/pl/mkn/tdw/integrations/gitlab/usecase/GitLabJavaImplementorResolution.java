package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaImplementorResolution(
        GitLabJavaImplementorResolutionStatus status,
        String interfaceName,
        List<String> searchKeywords,
        List<GitLabJavaImplementorCandidate> candidates,
        List<String> limitations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaImplementorResolution {
        status = status != null ? status : GitLabJavaImplementorResolutionStatus.INVALID_REQUEST;
        interfaceName = GitLabEndpointUseCaseModelSupport.trimToNull(interfaceName);
        searchKeywords = GitLabEndpointUseCaseModelSupport.copyStrings(searchKeywords);
        candidates = GitLabEndpointUseCaseModelSupport.copy(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
