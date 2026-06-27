package pl.mkn.tdw.api.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabRepositoryFilesByPathApiService {

    static final int DEFAULT_MAX_CHARACTERS_PER_FILE = 4_000;
    static final int DEFAULT_MAX_TOTAL_CHARACTERS = 60_000;
    static final int MAX_FILE_COUNT = 100;
    static final int MAX_CHARACTERS_PER_FILE = 120_000;
    static final int MAX_TOTAL_CHARACTERS = 500_000;
    private static final int PREVIEW_MAX_CHARACTERS = 200;

    private final GitLabRepositoryPort gitLabRepositoryPort;

    public GitLabRepositoryFilesByPathApiResponse readFiles(GitLabRepositoryFilesByPathApiRequest request) {
        var requestedFilePaths = normalizeRepositoryFilePaths(request.filePaths(), request.projectName());
        var processedFilePaths = requestedFilePaths.stream()
                .limit(MAX_FILE_COUNT)
                .toList();
        var maxCharactersPerFile = normalizeLimit(
                request.maxCharactersPerFile(),
                DEFAULT_MAX_CHARACTERS_PER_FILE,
                MAX_CHARACTERS_PER_FILE
        );
        var maxTotalCharacters = normalizeLimit(
                request.maxTotalCharacters(),
                DEFAULT_MAX_TOTAL_CHARACTERS,
                MAX_TOTAL_CHARACTERS
        );

        var remainingCharacters = maxTotalCharacters;
        var totalReturnedCharacters = 0;
        var returnedFileCount = 0;
        var failedFileCount = 0;
        var totalCharacterLimitReached = false;
        var results = new ArrayList<GitLabRepositoryFileByPathApiResult>();

        for (var filePath : processedFilePaths) {
            if (remainingCharacters <= 0) {
                totalCharacterLimitReached = true;
                break;
            }

            var effectiveFileLimit = Math.min(maxCharactersPerFile, remainingCharacters);
            try {
                var fileContent = gitLabRepositoryPort.readFile(
                        request.group(),
                        request.projectName(),
                        request.branch(),
                        filePath,
                        effectiveFileLimit
                );
                var content = fileContent != null ? fileContent.content() : null;
                var returnedCharacters = content != null ? content.length() : 0;
                results.add(successResult(request, filePath, fileContent, content, returnedCharacters));
                returnedFileCount++;
                totalReturnedCharacters += returnedCharacters;
                remainingCharacters -= returnedCharacters;
                if (remainingCharacters <= 0) {
                    totalCharacterLimitReached = true;
                }
            } catch (RuntimeException exception) {
                failedFileCount++;
                var error = toolErrorMessage(exception);
                log.warn(
                        "GitLab files-by-path manual read failed partially. group={} projectName={} branch={} filePath={} reason={}",
                        request.group(),
                        request.projectName(),
                        request.branch(),
                        filePath,
                        error
                );
                results.add(new GitLabRepositoryFileByPathApiResult(
                        request.group(),
                        request.projectName(),
                        request.branch(),
                        filePath,
                        null,
                        false,
                        inferRole(filePath, null),
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "SKIPPED",
                        "File content read failed before metadata lookup.",
                        error
                ));
            }
        }

        return new GitLabRepositoryFilesByPathApiResponse(
                request.group(),
                request.projectName(),
                request.branch(),
                requestedFilePaths.size(),
                results.size(),
                returnedFileCount,
                failedFileCount,
                totalReturnedCharacters,
                requestedFilePaths.size() > MAX_FILE_COUNT,
                totalCharacterLimitReached,
                results
        );
    }

    private GitLabRepositoryFileByPathApiResult successResult(
            GitLabRepositoryFilesByPathApiRequest request,
            String requestedFilePath,
            GitLabRepositoryFileContent fileContent,
            String content,
            int returnedCharacters
    ) {
        var filePath = responseFilePath(fileContent, requestedFilePath);
        var metadata = readMetadata(request, filePath);
        return new GitLabRepositoryFileByPathApiResult(
                responseGroup(fileContent, request.group()),
                responseProjectName(fileContent, request.projectName()),
                responseBranch(fileContent, request.branch()),
                filePath,
                content,
                fileContent != null && fileContent.truncated(),
                inferRole(filePath, content),
                returnedCharacters,
                metadata.sizeBytes(),
                metadata.contentSha256(),
                metadata.blobId(),
                metadata.commitId(),
                metadata.lastCommitId(),
                metadata.lastModifiedAt(),
                metadata.status(),
                metadata.error(),
                null
        );
    }

    private FileMetadataSnapshot readMetadata(GitLabRepositoryFilesByPathApiRequest request, String filePath) {
        try {
            var metadata = gitLabRepositoryPort.readFileMetadata(
                    request.group(),
                    request.projectName(),
                    request.branch(),
                    filePath
            );
            return FileMetadataSnapshot.from(metadata);
        } catch (RuntimeException exception) {
            var error = toolErrorMessage(exception);
            log.warn(
                    "GitLab files-by-path metadata read failed. group={} projectName={} branch={} filePath={} reason={}",
                    request.group(),
                    request.projectName(),
                    request.branch(),
                    filePath,
                    error
            );
            return FileMetadataSnapshot.failed(error);
        }
    }

    private List<String> normalizeRepositoryFilePaths(List<String> filePaths, String projectName) {
        var normalizedPaths = new LinkedHashSet<String>();
        for (var filePath : filePaths != null ? filePaths : List.<String>of()) {
            var normalizedPath = normalizeRepositoryFilePath(filePath, projectName);
            if (StringUtils.hasText(normalizedPath)) {
                normalizedPaths.add(normalizedPath);
            }
        }
        return List.copyOf(normalizedPaths);
    }

    private String normalizeRepositoryFilePath(String filePath, String projectName) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }

        var normalizedPath = filePath.trim().replace('\\', '/');
        var nextReadHintIndex = normalizedPath.indexOf(" via gitlab_");
        if (nextReadHintIndex > 0) {
            normalizedPath = normalizedPath.substring(0, nextReadHintIndex).trim();
        }
        var lineHintIndex = normalizedPath.indexOf(" lines ");
        if (lineHintIndex > 0) {
            normalizedPath = normalizedPath.substring(0, lineHintIndex).trim();
        }
        var projectSeparatorIndex = normalizedPath.indexOf(':');
        if (projectSeparatorIndex > 0 && projectSeparatorIndex + 1 < normalizedPath.length()) {
            var candidateProjectName = normalizedPath.substring(0, projectSeparatorIndex).trim();
            var candidateFilePath = normalizedPath.substring(projectSeparatorIndex + 1).trim();
            if (StringUtils.hasText(candidateFilePath)
                    && (candidateProjectName.equals(projectName) || candidateFilePath.startsWith("src/"))) {
                normalizedPath = candidateFilePath;
            }
        }
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    private int normalizeLimit(Integer value, int defaultValue, int maxValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private String inferRole(String filePath, String contentOrReason) {
        var value = (safeValue(filePath) + "\n" + safeValue(contentOrReason)).toLowerCase(Locale.ROOT);

        if (containsAny(value, "controller", "resource", "endpoint")) {
            return "entrypoint";
        }
        if (containsAny(value, "repository", "jparepository", "crudrepository", "dao")) {
            return "repository";
        }
        if (containsAny(value, "mapper", "converter", "assembler")) {
            return "mapper";
        }
        if (containsAny(value, "validator", "validation")) {
            return "validator";
        }
        if (containsAny(value, "client", "gateway", "http", "resttemplate", "webclient", "feign")) {
            return "downstream-client";
        }
        if (containsAny(value, "service", "facade", "orchestrator", "processor", "manager")) {
            return "service-or-orchestrator";
        }
        if (containsAny(value, "entity", "embeddable", "mappedsuperclass")) {
            return "entity";
        }
        if (containsAny(value, "configuration", "properties", "config")) {
            return "configuration";
        }

        return "other";
    }

    private boolean containsAny(String value, String... needles) {
        for (var needle : needles) {
            if (needle != null && value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String toolErrorMessage(RuntimeException exception) {
        var message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
        return exception.getClass().getSimpleName() + ": " + abbreviate(message, PREVIEW_MAX_CHARACTERS);
    }

    private String abbreviate(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxCharacters
                ? normalized.substring(0, maxCharacters) + "..."
                : normalized;
    }

    private String responseGroup(GitLabRepositoryFileContent fileContent, String fallbackGroup) {
        return fileContent != null && StringUtils.hasText(fileContent.group())
                ? fileContent.group()
                : fallbackGroup;
    }

    private String responseProjectName(GitLabRepositoryFileContent fileContent, String fallbackProjectName) {
        return fileContent != null && StringUtils.hasText(fileContent.projectName())
                ? fileContent.projectName()
                : fallbackProjectName;
    }

    private String responseBranch(GitLabRepositoryFileContent fileContent, String fallbackBranch) {
        return fileContent != null && StringUtils.hasText(fileContent.branch())
                ? fileContent.branch()
                : fallbackBranch;
    }

    private String responseFilePath(GitLabRepositoryFileContent fileContent, String fallbackFilePath) {
        return fileContent != null && StringUtils.hasText(fileContent.filePath())
                ? fileContent.filePath()
                : fallbackFilePath;
    }

    private String safeValue(String value) {
        return value != null ? value : "";
    }

    private record FileMetadataSnapshot(
            Long sizeBytes,
            String contentSha256,
            String blobId,
            String commitId,
            String lastCommitId,
            String lastModifiedAt,
            String status,
            String error
    ) {
        private static FileMetadataSnapshot from(GitLabRepositoryFileMetadata metadata) {
            if (metadata == null) {
                return unavailable();
            }

            return new FileMetadataSnapshot(
                    metadata.sizeBytes(),
                    metadata.contentSha256(),
                    metadata.blobId(),
                    metadata.commitId(),
                    metadata.lastCommitId(),
                    metadata.lastModifiedAt(),
                    "RESOLVED",
                    null
            );
        }

        private static FileMetadataSnapshot unavailable() {
            return new FileMetadataSnapshot(null, null, null, null, null, null, "UNAVAILABLE", null);
        }

        private static FileMetadataSnapshot failed(String error) {
            return new FileMetadataSnapshot(null, null, null, null, null, null, "FAILED", error);
        }
    }
}
