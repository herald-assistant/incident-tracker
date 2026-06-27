package pl.mkn.tdw.api.gitlab;

import java.util.List;

public record GitLabRepositoryFilesByPathApiResponse(
        String group,
        String projectName,
        String branch,
        int requestedFileCount,
        int processedFileCount,
        int returnedFileCount,
        int failedFileCount,
        int totalReturnedCharacters,
        boolean fileCountTruncated,
        boolean totalCharacterLimitReached,
        List<GitLabRepositoryFileByPathApiResult> files
) {
    public GitLabRepositoryFilesByPathApiResponse {
        files = files != null ? List.copyOf(files) : List.of();
    }
}
