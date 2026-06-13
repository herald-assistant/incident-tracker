package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseSourceSnapshot(
        String group,
        String projectName,
        String branch,
        String sourcePathPrefix,
        GitLabEndpointUseCaseIndexStatus indexStatus,
        int discoveredBlobCount,
        int eligibleSourceFileCount,
        int maxSourceFiles,
        int maxFileCharacters,
        boolean sourceFileLimitReached,
        boolean readTruncationDetected,
        List<GitLabEndpointUseCaseSourceFile> files,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseSourceSnapshot {
        indexStatus = indexStatus != null ? indexStatus : GitLabEndpointUseCaseIndexStatus.NOT_BUILT;
        files = files != null ? List.copyOf(files) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
