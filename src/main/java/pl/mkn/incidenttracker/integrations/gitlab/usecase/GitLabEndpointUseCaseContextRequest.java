package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitLabEndpointUseCaseContextRequest(
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @Size(max = 500, message = "endpointId must contain at most 500 characters")
        String endpointId,
        @Size(max = 20, message = "httpMethod must contain at most 20 characters")
        String httpMethod,
        @Size(max = 300, message = "endpointPath must contain at most 300 characters")
        String endpointPath,
        @Size(max = 300, message = "sourcePathPrefix must contain at most 300 characters")
        String sourcePathPrefix,
        GitLabEndpointUseCaseOutputMode outputMode,
        @Min(value = 1, message = "maxDepth must be at least 1")
        @Max(value = 20, message = "maxDepth must be at most 20")
        Integer maxDepth,
        @Min(value = 1, message = "maxNodes must be at least 1")
        @Max(value = 200, message = "maxNodes must be at most 200")
        Integer maxNodes,
        Boolean includeAsyncConsumers,
        @Size(max = 500, message = "reason must contain at most 500 characters")
        String reason
) {
    public static final String DEFAULT_SOURCE_PATH_PREFIX = "src/main/java";
    public static final int DEFAULT_MAX_DEPTH = 8;
    public static final int DEFAULT_MAX_NODES = 80;

    public GitLabEndpointUseCaseContextRequest {
        sourcePathPrefix = hasText(sourcePathPrefix) ? normalizePathPrefix(sourcePathPrefix) : DEFAULT_SOURCE_PATH_PREFIX;
        outputMode = outputMode != null ? outputMode : GitLabEndpointUseCaseOutputMode.COMPACT;
        maxDepth = maxDepth != null ? maxDepth : DEFAULT_MAX_DEPTH;
        maxNodes = maxNodes != null ? maxNodes : DEFAULT_MAX_NODES;
        includeAsyncConsumers = includeAsyncConsumers != null && includeAsyncConsumers;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizePathPrefix(String value) {
        var normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return hasText(normalized) ? normalized : DEFAULT_SOURCE_PATH_PREFIX;
    }
}
