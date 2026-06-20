package pl.mkn.incidenttracker.integrations.gitlab.source;

import java.util.List;

public record GitLabJavaMethodSliceResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        String status,
        String declaringTypeName,
        List<GitLabJavaMethodSliceMethodSelector> requestedMethods,
        int returnedLineStart,
        int returnedLineEnd,
        int totalLines,
        String content,
        int returnedCharacters,
        boolean truncated,
        List<String> includedImports,
        List<String> includedFields,
        List<GitLabJavaMethodSliceMethodCandidate> includedMethods,
        int omittedFieldCount,
        int omittedMethodCount,
        List<GitLabJavaMethodSliceMethodCandidate> candidates,
        List<String> limitations
) {
    public GitLabJavaMethodSliceResponse {
        requestedMethods = requestedMethods != null ? List.copyOf(requestedMethods) : List.of();
        includedImports = includedImports != null ? List.copyOf(includedImports) : List.of();
        includedFields = includedFields != null ? List.copyOf(includedFields) : List.of();
        includedMethods = includedMethods != null ? List.copyOf(includedMethods) : List.of();
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
