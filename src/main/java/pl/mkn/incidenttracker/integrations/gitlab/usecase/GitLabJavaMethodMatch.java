package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaMethodMatch(
        String methodName,
        String declaringTypeSimpleName,
        String declaringTypeRelativeName,
        String declaringTypeQualifiedName,
        GitLabJavaTypeKind declaringTypeKind,
        String filePath,
        int lineStart,
        int lineEnd,
        int parameterCount,
        List<String> parameterTypes,
        List<String> parameterNames,
        String returnType,
        List<String> modifiers,
        boolean publicMethod,
        boolean protectedMethod,
        boolean privateMethod,
        boolean staticMethod,
        boolean defaultMethod,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabJavaMethodMatch {
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        declaringTypeSimpleName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeSimpleName);
        declaringTypeRelativeName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeRelativeName);
        declaringTypeQualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeQualifiedName);
        declaringTypeKind = declaringTypeKind != null ? declaringTypeKind : GitLabJavaTypeKind.UNKNOWN;
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        parameterCount = Math.max(0, parameterCount);
        parameterTypes = GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes);
        parameterNames = GitLabEndpointUseCaseModelSupport.copyStrings(parameterNames);
        returnType = GitLabEndpointUseCaseModelSupport.trimToNull(returnType);
        modifiers = GitLabEndpointUseCaseModelSupport.copyStrings(modifiers);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
