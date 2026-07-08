package pl.mkn.tdw.integrations.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.List;

public record GitLabRepositoryEndpointListRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @Size(max = 200, message = "endpointPathPrefix must contain at most 200 characters")
        String endpointPathPrefix,
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        List<@Size(max = 250, message = "pathPrefix must contain at most 250 characters") String> pathPrefixes,
        @Min(value = 1, message = "maxScannedFiles must be at least 1")
        @Max(value = 250, message = "maxScannedFiles must be at most 250")
        Integer maxScannedFiles,
        boolean refreshCache
) {

    public GitLabRepositoryEndpointListRequest {
        pathPrefixes = normalizePathPrefixes(pathPrefixes);
    }

    public GitLabRepositoryEndpointListRequest(
            String group,
            String projectName,
            String branch,
            String endpointPathPrefix,
            String httpMethod,
            Integer maxScannedFiles
    ) {
        this(group, projectName, branch, endpointPathPrefix, httpMethod, List.of(), maxScannedFiles, false);
    }

    public GitLabRepositoryEndpointListRequest(
            String group,
            String projectName,
            String branch,
            String endpointPathPrefix,
            String httpMethod,
            Integer maxScannedFiles,
            boolean refreshCache
    ) {
        this(group, projectName, branch, endpointPathPrefix, httpMethod, List.of(), maxScannedFiles, refreshCache);
    }

    private static List<String> normalizePathPrefixes(List<String> pathPrefixes) {
        var normalized = new LinkedHashSet<String>();
        for (var pathPrefix : pathPrefixes != null ? pathPrefixes : List.<String>of()) {
            if (pathPrefix == null || pathPrefix.isBlank()) {
                continue;
            }
            var value = pathPrefix.trim().replace('\\', '/');
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }
}
