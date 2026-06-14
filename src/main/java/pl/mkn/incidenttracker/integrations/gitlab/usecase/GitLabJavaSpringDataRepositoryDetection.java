package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaSpringDataRepositoryDetection(
        GitLabJavaSpringDataRepositoryStatus status,
        GitLabJavaSpringDataRepository repository,
        List<String> limitations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaSpringDataRepositoryDetection {
        status = status != null ? status : GitLabJavaSpringDataRepositoryStatus.NOT_SPRING_DATA_REPOSITORY;
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
