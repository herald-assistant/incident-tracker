package pl.mkn.tdw.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseRepositoryContext(
        String group,
        String projectName,
        String branch
) {
    public GitLabEndpointUseCaseRepositoryContext {
        group = GitLabEndpointUseCaseModelSupport.trimToNull(group);
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        branch = GitLabEndpointUseCaseModelSupport.trimToNull(branch);
    }
}
