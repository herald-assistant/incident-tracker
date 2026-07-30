package pl.mkn.tdw.integrations.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GitLabNamedExactRepositoryAdapter implements GitLabExactRepositoryPort {

    private static final Pattern SAFE_PROJECT_PATH =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}");
    private static final Pattern SAFE_REF =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");
    private static final Pattern SAFE_FILE_PATH =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,1023}");

    private final GitLabNamedConnectionRegistry connectionRegistry;
    private final GitLabRestClientFactory restClientFactory;

    @Override
    public boolean branchExists(String connectionId, String projectPath, String branch) {
        var target = target(connectionId, projectPath, branch, null);
        try {
            client(target.connection()).get()
                    .uri(branchUri(target, branch))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }
            throw GitLabExactReadException.upstream(
                    "branch lookup",
                    target.connection().id(),
                    exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw GitLabExactReadException.transportFailure(
                    "branch lookup",
                    target.connection().id(),
                    exception
            );
        }
    }

    @Override
    public GitLabExactFileContent readFile(
            String connectionId,
            String projectPath,
            String ref,
            String filePath,
            int maxCharacters
    ) {
        var target = target(connectionId, projectPath, ref, filePath);
        try {
            var content = client(target.connection()).get()
                    .uri(rawFileUri(target))
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .body(String.class);
            var safeContent = content != null ? content : "";
            var limit = Math.min(
                    maxCharacters > 0 ? maxCharacters : connectionRegistry.maxFileCharacters(),
                    connectionRegistry.maxFileCharacters()
            );
            var returned = safeContent.length() > limit ? safeContent.substring(0, limit) : safeContent;
            return new GitLabExactFileContent(
                    target.connection().id(),
                    target.projectPath(),
                    target.ref(),
                    target.filePath(),
                    returned,
                    returned.length(),
                    returned.length() != safeContent.length()
            );
        } catch (RestClientResponseException exception) {
            throw GitLabExactReadException.upstream(
                    "file read",
                    target.connection().id(),
                    exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw GitLabExactReadException.transportFailure(
                    "file read",
                    target.connection().id(),
                    exception
            );
        }
    }

    @Override
    public GitLabExactFileMetadata readFileMetadata(
            String connectionId,
            String projectPath,
            String ref,
            String filePath
    ) {
        var target = target(connectionId, projectPath, ref, filePath);
        try {
            var response = client(target.connection()).get()
                    .uri(fileMetadataUri(target))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(FileMetadataResponse.class);
            if (response == null) {
                throw GitLabExactReadException.upstream(
                        "file metadata read",
                        target.connection().id(),
                        502,
                        null
                );
            }
            var lastModifiedAt = readLastModifiedAt(target, response.lastCommitId());
            return new GitLabExactFileMetadata(
                    target.connection().id(),
                    target.projectPath(),
                    target.ref(),
                    StringUtils.hasText(response.filePath()) ? response.filePath() : target.filePath(),
                    response.blobId(),
                    response.commitId(),
                    response.lastCommitId(),
                    lastModifiedAt,
                    response.contentSha256(),
                    response.sizeBytes()
            );
        } catch (GitLabExactReadException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw GitLabExactReadException.upstream(
                    "file metadata read",
                    target.connection().id(),
                    exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw GitLabExactReadException.transportFailure(
                    "file metadata read",
                    target.connection().id(),
                    exception
            );
        }
    }

    private String readLastModifiedAt(Target target, String lastCommitId) {
        if (!StringUtils.hasText(lastCommitId)) {
            return null;
        }
        try {
            var response = client(target.connection()).get()
                    .uri(commitUri(target, lastCommitId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CommitResponse.class);
            return response != null ? response.committedDate() : null;
        } catch (RestClientResponseException exception) {
            throw GitLabExactReadException.upstream(
                    "commit metadata read",
                    target.connection().id(),
                    exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw GitLabExactReadException.transportFailure(
                    "commit metadata read",
                    target.connection().id(),
                    exception
            );
        }
    }

    private Target target(String connectionId, String projectPath, String ref, String filePath) {
        var connection = connectionRegistry.require(connectionId);
        var normalizedProjectPath = normalizeSafePath(projectPath, SAFE_PROJECT_PATH, "GitLab project path");
        var normalizedRef = normalizeSafePath(ref, SAFE_REF, "GitLab ref");
        var normalizedFilePath = filePath != null
                ? normalizeSafePath(filePath, SAFE_FILE_PATH, "GitLab file path")
                : null;
        return new Target(connection, normalizedProjectPath, normalizedRef, normalizedFilePath);
    }

    private String normalizeSafePath(String value, Pattern pattern, String label) {
        if (!StringUtils.hasText(value)) {
            throw GitLabExactReadException.invalidTarget(label + " is required.");
        }
        var normalized = value.trim().replace('\\', '/');
        if (!pattern.matcher(normalized).matches()
                || normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("//")
                || normalized.contains("..")
                || normalized.contains("@{")) {
            throw GitLabExactReadException.invalidTarget(label + " is invalid.");
        }
        return normalized;
    }

    private RestClient client(GitLabConnectionDetails connection) {
        return restClientFactory.create(connection);
    }

    private URI branchUri(Target target, String branch) {
        return URI.create(apiBaseUrl(target.connection())
                + "/projects/" + encodePathSegment(target.projectPath())
                + "/repository/branches/" + encodePathSegment(branch));
    }

    private URI rawFileUri(Target target) {
        return URI.create(apiBaseUrl(target.connection())
                + "/projects/" + encodePathSegment(target.projectPath())
                + "/repository/files/" + encodePathSegment(target.filePath())
                + "/raw?ref=" + encodeQueryParam(target.ref()));
    }

    private URI fileMetadataUri(Target target) {
        return URI.create(apiBaseUrl(target.connection())
                + "/projects/" + encodePathSegment(target.projectPath())
                + "/repository/files/" + encodePathSegment(target.filePath())
                + "?ref=" + encodeQueryParam(target.ref()));
    }

    private URI commitUri(Target target, String commitId) {
        return URI.create(apiBaseUrl(target.connection())
                + "/projects/" + encodePathSegment(target.projectPath())
                + "/repository/commits/" + encodePathSegment(commitId)
                + "?stats=false");
    }

    private String apiBaseUrl(GitLabConnectionDetails connection) {
        return connection.baseUrl() + "/api/v4";
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private record Target(
            GitLabConnectionDetails connection,
            String projectPath,
            String ref,
            String filePath
    ) {
    }

    private record FileMetadataResponse(
            @JsonProperty("file_path")
            String filePath,
            @JsonProperty("blob_id")
            String blobId,
            @JsonProperty("commit_id")
            String commitId,
            @JsonProperty("last_commit_id")
            String lastCommitId,
            @JsonProperty("content_sha256")
            String contentSha256,
            @JsonProperty("size")
            Long sizeBytes
    ) {
    }

    private record CommitResponse(
            String id,
            @JsonProperty("committed_date")
            String committedDate
    ) {
    }
}
