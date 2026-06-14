package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseRepositoryContext(
        String group,
        String projectName,
        String branch,
        String sourcePathPrefix
) {
    public GitLabEndpointUseCaseRepositoryContext {
        group = GitLabEndpointUseCaseModelSupport.trimToNull(group);
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        branch = GitLabEndpointUseCaseModelSupport.trimToNull(branch);
        sourcePathPrefix = GitLabEndpointUseCaseModelSupport.normalizeSourcePathPrefix(sourcePathPrefix);
    }
}
