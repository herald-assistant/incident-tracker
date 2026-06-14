package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaDependencyModel(
        GitLabJavaBeanCandidate bean,
        List<GitLabJavaInjectedDependency> injectedDependencies,
        List<String> limitations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaDependencyModel {
        injectedDependencies = GitLabEndpointUseCaseModelSupport.copy(injectedDependencies);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
