package pl.mkn.tdw.integrations.gitlab;

import java.time.Instant;
import java.util.List;

public record GitLabRepositoryEndpointListResult(
        String group,
        String projectName,
        String branch,
        String endpointPathPrefix,
        String httpMethod,
        Instant dataCollectedAt,
        int candidateFileCount,
        int scannedFileCount,
        boolean scannedFileLimitReached,
        List<GitLabRepositoryEndpoint> endpoints,
        List<String> limitations
) {
    public GitLabRepositoryEndpointListResult {
        endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public GitLabRepositoryEndpointListResult(
            String group,
            String projectName,
            String branch,
            String endpointPathPrefix,
            String httpMethod,
            int candidateFileCount,
            int scannedFileCount,
            boolean scannedFileLimitReached,
            List<GitLabRepositoryEndpoint> endpoints,
            List<String> limitations
    ) {
        this(
                group,
                projectName,
                branch,
                endpointPathPrefix,
                httpMethod,
                null,
                candidateFileCount,
                scannedFileCount,
                scannedFileLimitReached,
                endpoints,
                limitations
        );
    }
}
