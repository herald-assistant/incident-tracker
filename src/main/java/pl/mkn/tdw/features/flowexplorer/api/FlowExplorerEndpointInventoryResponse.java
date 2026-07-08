package pl.mkn.tdw.features.flowexplorer.api;

import java.time.Instant;
import java.util.List;

public record FlowExplorerEndpointInventoryResponse(
        String systemId,
        String requestedBranch,
        String resolvedRef,
        String gitLabGroup,
        String endpointPathPrefix,
        String httpMethod,
        int repositoryCount,
        int scannedRepositoryCount,
        int endpointCount,
        int candidateFileCount,
        int scannedFileCount,
        boolean scannedFileLimitReached,
        Instant dataCollectedAt,
        List<RepositoryInventoryResponse> repositories,
        List<EndpointOptionResponse> endpoints,
        List<String> limitations
) {

    public FlowExplorerEndpointInventoryResponse {
        repositories = repositories != null ? List.copyOf(repositories) : List.of();
        endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public FlowExplorerEndpointInventoryResponse(
            String systemId,
            String requestedBranch,
            String resolvedRef,
            String gitLabGroup,
            String endpointPathPrefix,
            String httpMethod,
            int repositoryCount,
            int scannedRepositoryCount,
            int endpointCount,
            int candidateFileCount,
            int scannedFileCount,
            boolean scannedFileLimitReached,
            List<RepositoryInventoryResponse> repositories,
            List<EndpointOptionResponse> endpoints,
            List<String> limitations
    ) {
        this(
                systemId,
                requestedBranch,
                resolvedRef,
                gitLabGroup,
                endpointPathPrefix,
                httpMethod,
                repositoryCount,
                scannedRepositoryCount,
                endpointCount,
                candidateFileCount,
                scannedFileCount,
                scannedFileLimitReached,
                null,
                repositories,
                endpoints,
                limitations
        );
    }

    public record RepositoryInventoryResponse(
            String repositoryId,
            String projectName,
            String projectPath,
            String resolvedRef,
            String searchMode,
            List<String> pathPrefixes,
            int candidateFileCount,
            int scannedFileCount,
            boolean scannedFileLimitReached,
            int endpointCount,
            List<String> limitations
    ) {

        public RepositoryInventoryResponse {
            pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }

    public record EndpointOptionResponse(
            String endpointId,
            String method,
            List<String> methods,
            String path,
            String pathExpression,
            String summary,
            String description,
            String operationId,
            List<String> tags,
            String controllerClass,
            String handlerMethod,
            EndpointSourceResponse source,
            List<EndpointParameterResponse> parameters,
            String confidence,
            List<String> limitations,
            List<String> suggestedNextReads,
            EndpointTooltipDetailsResponse tooltipDetails
    ) {

        public EndpointOptionResponse {
            methods = methods != null ? List.copyOf(methods) : List.of();
            tags = tags != null ? List.copyOf(tags) : List.of();
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
            suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
        }
    }

    public record EndpointSourceResponse(
            String repositoryId,
            String projectName,
            String projectPath,
            String filePath,
            int lineStart,
            int lineEnd
    ) {
    }

    public record EndpointParameterResponse(
            String name,
            String in,
            boolean required,
            String type,
            String description
    ) {
    }

    public record EndpointTooltipDetailsResponse(
            String documentationSource,
            String summary,
            String description,
            String operationId,
            List<String> tags,
            List<EndpointParameterResponse> parameters,
            List<String> requestTypes,
            List<String> responseTypes,
            List<String> annotations,
            List<String> limitations,
            List<String> suggestedNextReads
    ) {

        public EndpointTooltipDetailsResponse {
            tags = tags != null ? List.copyOf(tags) : List.of();
            parameters = parameters != null ? List.copyOf(parameters) : List.of();
            requestTypes = requestTypes != null ? List.copyOf(requestTypes) : List.of();
            responseTypes = responseTypes != null ? List.copyOf(responseTypes) : List.of();
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
            suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
        }
    }
}
