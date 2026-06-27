package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMethodResolution(
        GitLabJavaMethodResolutionStatus status,
        GitLabJavaMethodMatch method,
        List<GitLabJavaMethodMatch> candidates,
        List<String> limitations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMethodResolution {
        status = status != null ? status : GitLabJavaMethodResolutionStatus.INVALID_REQUEST;
        candidates = GitLabEndpointUseCaseModelSupport.copy(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
