package pl.mkn.tdw.integrations.gitlab.usecase;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

public record GitLabEndpointUseCaseContextRequest(
        String projectName,
        String endpointId,
        String httpMethod,
        String endpointPath,
        List<String> pathPrefixes,
        Integer maxDepth,
        Integer maxFiles
) {
    public static final int DEFAULT_MAX_DEPTH = 5;
    public static final int MAX_MAX_DEPTH = 8;
    public static final int DEFAULT_MAX_FILES = 60;
    public static final int MAX_MAX_FILES = 100;

    public GitLabEndpointUseCaseContextRequest(
            String projectName,
            String endpointId,
            String httpMethod,
            String endpointPath,
            Integer maxDepth,
            Integer maxFiles
    ) {
        this(projectName, endpointId, httpMethod, endpointPath, List.of(), maxDepth, maxFiles);
    }

    public GitLabEndpointUseCaseContextRequest {
        projectName = GitLabEndpointUseCaseModelSupport.trimToNull(projectName);
        endpointId = GitLabEndpointUseCaseModelSupport.trimToNull(endpointId);
        httpMethod = GitLabEndpointUseCaseModelSupport.normalizeHttpMethod(httpMethod);
        endpointPath = GitLabEndpointUseCaseModelSupport.normalizeEndpointPath(endpointPath);
        pathPrefixes = normalizePathPrefixes(pathPrefixes);
        maxDepth = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxDepth, DEFAULT_MAX_DEPTH, MAX_MAX_DEPTH);
        maxFiles = GitLabEndpointUseCaseModelSupport.normalizeLimit(maxFiles, DEFAULT_MAX_FILES, MAX_MAX_FILES);
    }

    private static List<String> normalizePathPrefixes(List<String> pathPrefixes) {
        if (pathPrefixes == null || pathPrefixes.isEmpty()) {
            return List.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var prefix : pathPrefixes) {
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            var value = GitLabEndpointUseCaseModelSupport.normalizeFilePath(prefix);
            while (value != null && value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }
}
