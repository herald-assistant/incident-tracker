package pl.mkn.incidenttracker.integrations.gitlab.usecase;

public record GitLabEndpointUseCaseRepositoryContext(
        String group,
        String projectName,
        String requestedBranch,
        String sourcePathPrefix,
        GitLabEndpointUseCaseIndexStatus indexStatus
) {
    public GitLabEndpointUseCaseRepositoryContext {
        indexStatus = indexStatus != null ? indexStatus : GitLabEndpointUseCaseIndexStatus.NOT_BUILT;
    }
}
