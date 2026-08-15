package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendRouteCatalog(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        List<GitLabFrontendWorkspaceSignal> workspaceSignals,
        List<GitLabFrontendRouteEntry> entries,
        List<GitLabFrontendDiagnostic> diagnostics,
        int repositoryFileCount,
        int scannedRouteFileCount,
        boolean inventoryTruncated,
        boolean routeCatalogTruncated
) {

    public GitLabFrontendRouteCatalog {
        workspaceSignals = workspaceSignals != null ? List.copyOf(workspaceSignals) : List.of();
        entries = entries != null ? List.copyOf(entries) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}

