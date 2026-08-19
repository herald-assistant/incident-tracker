package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabAngularRouteBranchSliceFile(
        String path,
        String content,
        int sourceCharacters,
        int returnedCharacters,
        List<String> includedRouteNodeIds,
        List<String> includedImports,
        List<String> includedLocalDeclarations,
        List<String> unresolvedSymbols,
        int omittedImportCount,
        int omittedSiblingRouteCount,
        boolean truncated
) {
    public GitLabAngularRouteBranchSliceFile {
        includedRouteNodeIds = includedRouteNodeIds != null ? List.copyOf(includedRouteNodeIds) : List.of();
        includedImports = includedImports != null ? List.copyOf(includedImports) : List.of();
        includedLocalDeclarations = includedLocalDeclarations != null
                ? List.copyOf(includedLocalDeclarations)
                : List.of();
        unresolvedSymbols = unresolvedSymbols != null ? List.copyOf(unresolvedSymbols) : List.of();
    }
}
