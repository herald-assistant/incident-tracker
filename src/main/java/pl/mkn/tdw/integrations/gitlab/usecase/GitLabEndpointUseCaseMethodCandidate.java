package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;
import java.util.StringJoiner;

public record GitLabEndpointUseCaseMethodCandidate(
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
        List<String> modifiers,
        GitLabEndpointUseCaseFileRole role,
        int priority,
        int depth,
        String reason,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabEndpointUseCaseMethodCandidate {
        filePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        declaringTypeSimpleName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeSimpleName);
        declaringTypeRelativeName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeRelativeName);
        declaringTypeQualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(declaringTypeQualifiedName);
        declaringTypeKind = declaringTypeKind != null ? declaringTypeKind : GitLabJavaTypeKind.UNKNOWN;
        methodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
        parameterCount = Math.max(0, parameterCount);
        parameterTypes = GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes);
        parameterNames = GitLabEndpointUseCaseModelSupport.copyStrings(parameterNames);
        returnType = GitLabEndpointUseCaseModelSupport.trimToNull(returnType);
        modifiers = GitLabEndpointUseCaseModelSupport.copyStrings(modifiers);
        signature = GitLabEndpointUseCaseModelSupport.trimToNull(signature);
        if (signature == null) {
            signature = signature(methodName, parameterCount, parameterTypes, parameterNames, returnType);
        }
        role = role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN;
        priority = GitLabEndpointUseCaseModelSupport.normalizePriority(priority);
        depth = Math.max(0, depth);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }

    static GitLabEndpointUseCaseMethodCandidate from(
            GitLabJavaMethodMatch method,
            GitLabEndpointUseCaseFileRole role,
            int priority,
            int depth,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        if (method == null) {
            return null;
        }
        return new GitLabEndpointUseCaseMethodCandidate(
                method.filePath(),
                method.declaringTypeSimpleName(),
                method.declaringTypeRelativeName(),
                method.declaringTypeQualifiedName(),
                method.declaringTypeKind(),
                method.methodName(),
                null,
                method.lineStart(),
                method.lineEnd(),
                method.parameterCount(),
                method.parameterTypes(),
                method.parameterNames(),
                method.returnType(),
                method.modifiers(),
                role,
                priority,
                depth,
                reason,
                confidence != null ? confidence : method.confidence()
        );
    }

    String deduplicationKey() {
        return "%s|%s|%s|%s|%s|%s".formatted(
                filePath,
                declaringTypeQualifiedName,
                methodName,
                lineStart,
                lineEnd,
                String.join(",", parameterTypes)
        );
    }

    private static String signature(
            String methodName,
            int parameterCount,
            List<String> parameterTypes,
            List<String> parameterNames,
            String returnType
    ) {
        if (methodName == null) {
            return null;
        }
        var parameters = new StringJoiner(", ");
        var count = Math.max(parameterCount, Math.max(parameterTypes.size(), parameterNames.size()));
        for (var i = 0; i < count; i++) {
            var type = i < parameterTypes.size() ? parameterTypes.get(i) : null;
            var name = i < parameterNames.size() ? parameterNames.get(i) : null;
            if (type != null && name != null) {
                parameters.add(type + " " + name);
            } else if (type != null) {
                parameters.add(type);
            } else if (name != null) {
                parameters.add(name);
            }
        }
        var signature = methodName + "(" + parameters + ")";
        return returnType != null ? returnType + " " + signature : signature;
    }
}
