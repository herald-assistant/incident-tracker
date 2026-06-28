package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMethodUseCaseEntryMethod(
        GitLabJavaMethodUseCaseEntryStatus status,
        String requestedClassName,
        String requestedMethodName,
        String filePath,
        String declaringTypeSimpleName,
        String declaringTypeRelativeName,
        String declaringTypeQualifiedName,
        GitLabJavaTypeKind declaringTypeKind,
        String methodName,
        String signature,
        int lineStart,
        int lineEnd,
        int parameterCount,
        List<String> parameterTypes,
        List<String> parameterNames,
        String returnType,
        GitLabEndpointUseCaseConfidence confidence,
        List<GitLabJavaMethodUseCaseEntryCandidate> candidates,
        List<String> limitations
) {
    public GitLabJavaMethodUseCaseEntryMethod {
        status = status != null ? status : GitLabJavaMethodUseCaseEntryStatus.INVALID_REQUEST;
        requestedClassName = GitLabEndpointUseCaseModelSupport.trimToNull(requestedClassName);
        requestedMethodName = GitLabEndpointUseCaseModelSupport.trimToNull(requestedMethodName);
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        declaringTypeSimpleName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeSimpleName);
        declaringTypeRelativeName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeRelativeName);
        declaringTypeQualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeQualifiedName);
        declaringTypeKind = declaringTypeKind != null ? declaringTypeKind : GitLabJavaTypeKind.UNKNOWN;
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        signature = GitLabEndpointUseCaseModelSupport.trimToNull(signature);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        parameterCount = Math.max(0, parameterCount);
        parameterTypes = GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes);
        parameterNames = GitLabEndpointUseCaseModelSupport.copyStrings(parameterNames);
        returnType = GitLabEndpointUseCaseModelSupport.trimToNull(returnType);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        candidates = GitLabEndpointUseCaseModelSupport.copy(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }

    static GitLabJavaMethodUseCaseEntryMethod resolved(
            String requestedClassName,
            String requestedMethodName,
            GitLabJavaMethodMatch method,
            List<String> limitations
    ) {
        var candidate = GitLabJavaMethodUseCaseEntryCandidate.from(method, "Resolved entry method.");
        return new GitLabJavaMethodUseCaseEntryMethod(
                GitLabJavaMethodUseCaseEntryStatus.RESOLVED,
                requestedClassName,
                requestedMethodName,
                method.filePath(),
                method.declaringTypeSimpleName(),
                method.declaringTypeRelativeName(),
                method.declaringTypeQualifiedName(),
                method.declaringTypeKind(),
                method.methodName(),
                candidate != null ? candidate.signature() : null,
                method.lineStart(),
                method.lineEnd(),
                method.parameterCount(),
                method.parameterTypes(),
                method.parameterNames(),
                method.returnType(),
                method.confidence(),
                candidate != null ? List.of(candidate) : List.of(),
                limitations
        );
    }

    static GitLabJavaMethodUseCaseEntryMethod unresolved(
            GitLabJavaMethodUseCaseEntryStatus status,
            String requestedClassName,
            String requestedMethodName,
            List<GitLabJavaMethodUseCaseEntryCandidate> candidates,
            List<String> limitations
    ) {
        return new GitLabJavaMethodUseCaseEntryMethod(
                status,
                requestedClassName,
                requestedMethodName,
                null,
                null,
                null,
                null,
                GitLabJavaTypeKind.UNKNOWN,
                null,
                null,
                0,
                0,
                0,
                List.of(),
                List.of(),
                null,
                GitLabEndpointUseCaseConfidence.LOW,
                candidates,
                limitations
        );
    }

    public boolean resolved() {
        return status == GitLabJavaMethodUseCaseEntryStatus.RESOLVED;
    }
}
