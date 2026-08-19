package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabTypeScriptSymbolSliceResponse(
        GitLabFrontendRepositoryScope scope,
        String filePath,
        String status,
        String declaringTypeName,
        int lineStart,
        int lineEnd,
        int totalLines,
        int sourceCharacters,
        String content,
        int returnedCharacters,
        int savedCharacters,
        boolean truncated,
        List<String> includedImports,
        List<String> includedFields,
        List<GitLabTypeScriptSymbolCandidate> includedSymbols,
        int omittedImportCount,
        int omittedFieldCount,
        int omittedSymbolCount,
        List<GitLabTypeScriptDownstreamReference> downstreamReferences,
        List<GitLabTypeScriptSymbolCandidate> candidates,
        List<String> limitations
) {
    public GitLabTypeScriptSymbolSliceResponse {
        includedImports = includedImports != null ? List.copyOf(includedImports) : List.of();
        includedFields = includedFields != null ? List.copyOf(includedFields) : List.of();
        includedSymbols = includedSymbols != null ? List.copyOf(includedSymbols) : List.of();
        downstreamReferences = downstreamReferences != null ? List.copyOf(downstreamReferences) : List.of();
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
