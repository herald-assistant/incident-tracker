package pl.mkn.tdw.integrations.gitlab.usecase;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointDocumentation;

import java.util.List;
import java.util.Objects;

public record GitLabEndpointUseCaseEndpointContext(
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
        GitLabEndpointUseCaseConfidence confidence,
        List<String> limitations,
        List<String> suggestedNextReads
) {
    public GitLabEndpointUseCaseEndpointContext {
        endpointId = GitLabEndpointUseCaseModelSupport.trimToNull(endpointId);
        httpMethods = GitLabEndpointUseCaseModelSupport.copyStrings(httpMethods).stream()
                .map(GitLabEndpointUseCaseModelSupport::normalizeHttpMethod)
                .filter(Objects::nonNull)
                .toList();
        path = GitLabEndpointUseCaseModelSupport.normalizeEndpointPath(path);
        pathExpression = GitLabEndpointUseCaseModelSupport.trimToNull(pathExpression);
        controllerClass = GitLabEndpointUseCaseModelSupport.trimToNull(controllerClass);
        handlerMethod = GitLabEndpointUseCaseModelSupport.trimToNull(handlerMethod);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        requestTypes = GitLabEndpointUseCaseModelSupport.copyStrings(requestTypes);
        responseTypes = GitLabEndpointUseCaseModelSupport.copyStrings(responseTypes);
        annotations = GitLabEndpointUseCaseModelSupport.copyStrings(annotations);
        documentation = documentation != null && !documentation.empty() ? documentation : null;
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        suggestedNextReads = GitLabEndpointUseCaseModelSupport.copyStrings(suggestedNextReads);
    }

    public GitLabEndpointUseCaseEndpointContext(
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
            GitLabEndpointUseCaseConfidence confidence,
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
}
