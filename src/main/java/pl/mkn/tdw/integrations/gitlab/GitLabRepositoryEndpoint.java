package pl.mkn.tdw.integrations.gitlab;

import java.util.List;

public record GitLabRepositoryEndpoint(
        String endpointId,
        List<String> httpMethods,
        String path,
        String pathExpression,
        String controllerClass,
        String handlerMethod,
        String filePath,
        int lineStart,
        int lineEnd,
        List<String> requestTypes,
        List<String> responseTypes,
        List<String> annotations,
        GitLabRepositoryEndpointDocumentation documentation,
        String confidence,
        List<String> limitations,
        List<String> suggestedNextReads
) {
    public GitLabRepositoryEndpoint {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
        requestTypes = requestTypes != null ? List.copyOf(requestTypes) : List.of();
        responseTypes = responseTypes != null ? List.copyOf(responseTypes) : List.of();
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        documentation = documentation != null && !documentation.empty() ? documentation : null;
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
    }

    public GitLabRepositoryEndpoint(
            String endpointId,
            List<String> httpMethods,
            String path,
            String pathExpression,
            String controllerClass,
            String handlerMethod,
            String filePath,
            int lineStart,
            int lineEnd,
            List<String> requestTypes,
            List<String> responseTypes,
            List<String> annotations,
            String confidence,
            List<String> limitations,
            List<String> suggestedNextReads
    ) {
        this(
                endpointId,
                httpMethods,
                path,
                pathExpression,
                controllerClass,
                handlerMethod,
                filePath,
                lineStart,
                lineEnd,
                requestTypes,
                responseTypes,
                annotations,
                null,
                confidence,
                limitations,
                suggestedNextReads
        );
    }

    public GitLabRepositoryEndpoint withDocumentation(GitLabRepositoryEndpointDocumentation documentation) {
        return new GitLabRepositoryEndpoint(
                endpointId,
                httpMethods,
                path,
                pathExpression,
                controllerClass,
                handlerMethod,
                filePath,
                lineStart,
                lineEnd,
                requestTypes,
                responseTypes,
                annotations,
                documentation,
                confidence,
                limitations,
                suggestedNextReads
        );
    }
}
