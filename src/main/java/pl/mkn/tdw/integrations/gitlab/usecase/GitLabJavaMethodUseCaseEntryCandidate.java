package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;
import java.util.StringJoiner;

public record GitLabJavaMethodUseCaseEntryCandidate(
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
        String reason
) {
    public GitLabJavaMethodUseCaseEntryCandidate {
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
        if (signature == null) {
            signature = signature(methodName, parameterCount, parameterTypes, parameterNames, returnType);
        }
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
    }

    static GitLabJavaMethodUseCaseEntryCandidate from(GitLabJavaMethodMatch method, String reason) {
        if (method == null) {
            return null;
        }
        return new GitLabJavaMethodUseCaseEntryCandidate(
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
                method.confidence(),
                reason
        );
    }

    static GitLabJavaMethodUseCaseEntryCandidate sourceFileCandidate(String filePath, String className, String reason) {
        return new GitLabJavaMethodUseCaseEntryCandidate(
                filePath,
                className,
                className,
                className,
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
                reason
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
        for (var index = 0; index < count; index++) {
            var type = index < parameterTypes.size() ? parameterTypes.get(index) : null;
            var name = index < parameterNames.size() ? parameterNames.get(index) : null;
            if (type != null && name != null) {
                parameters.add(type + " " + name);
            } else if (type != null) {
                parameters.add(type);
            } else if (name != null) {
                parameters.add(name);
            }
        }
        var generatedSignature = methodName + "(" + parameters + ")";
        return returnType != null ? returnType + " " + generatedSignature : generatedSignature;
    }
}
