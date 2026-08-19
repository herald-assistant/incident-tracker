package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabAngularRouteBranchSliceResponse(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        String status,
        GitLabFrontendRouteNode screenNode,
        GitLabFrontendEffectiveRouteChain effectiveRouteChain,
        List<GitLabAngularRouteBranchSliceFile> files,
        List<GitLabAngularRouteChildReference> childRoutes,
        int sourceCharacters,
        int returnedCharacters,
        int savedCharacters,
        int omittedImportCount,
        int omittedSiblingRouteCount,
        boolean truncated,
        List<String> limitations,
        List<GitLabFrontendGraphDiagnostic> diagnostics
) {
    public GitLabAngularRouteBranchSliceResponse {
        files = files != null ? List.copyOf(files) : List.of();
        childRoutes = childRoutes != null ? List.copyOf(childRoutes) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
