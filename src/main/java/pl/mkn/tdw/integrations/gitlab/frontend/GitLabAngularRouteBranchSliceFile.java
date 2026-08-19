package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabAngularRouteBranchSliceFile(
        String path,
        String content,
        int sourceCharacters,
        int returnedCharacters,
        List<String> includedRouteNodeIds,
        List<String> includedImports,
        int omittedImportCount,
        int omittedSiblingRouteCount,
        boolean truncated
) {
    public GitLabAngularRouteBranchSliceFile {
        includedRouteNodeIds = includedRouteNodeIds != null ? List.copyOf(includedRouteNodeIds) : List.of();
        includedImports = includedImports != null ? List.copyOf(includedImports) : List.of();
    }
}
