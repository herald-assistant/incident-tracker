package pl.mkn.incidenttracker.integrations.gitlab.source;

import java.util.List;

public record GitLabJavaMethodSliceMethodCandidate(
        String declaringTypeName,
        String methodName,
        String signature,
        int lineStart,
        int lineEnd,
        int parameterCount,
        List<String> parameterTypes
) {
    public GitLabJavaMethodSliceMethodCandidate {
        parameterTypes = parameterTypes != null ? List.copyOf(parameterTypes) : List.of();
    }
}
