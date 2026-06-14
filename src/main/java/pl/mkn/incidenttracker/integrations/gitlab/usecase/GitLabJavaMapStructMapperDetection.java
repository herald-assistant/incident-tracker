package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMapStructMapperDetection(
        GitLabJavaMapStructMapperStatus status,
        GitLabJavaMapStructMapper mapper,
        List<String> limitations,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMapStructMapperDetection {
        status = status != null ? status : GitLabJavaMapStructMapperStatus.NOT_MAPSTRUCT_MAPPER;
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
