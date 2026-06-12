package pl.mkn.incidenttracker.integrations.gitlab;

public record GitLabRepositoryEndpointListRequest(
        String group,
        String projectName,
        String branch,
        String endpointPathPrefix,
        String httpMethod,
        String sourcePathPrefix,
        Integer maxScannedFiles
) {
}
