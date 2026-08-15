package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendScreenSourceContext(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        GitLabFrontendRouteEntry screen,
        List<GitLabFrontendWorkspaceSignal> workspaceSignals,
        List<GitLabFrontendSourceFile> sourceFiles,
        List<GitLabFrontendTechnicalSignal> technicalSignals,
        List<GitLabFrontendContextCoverage> coverage,
        List<GitLabFrontendDiagnostic> diagnostics,
        int repositoryFileCount,
        int scannedRouteFileCount,
        boolean inventoryTruncated,
        boolean routeCatalogTruncated,
        int totalReturnedCharacters,
        boolean truncated
) {

    public GitLabFrontendScreenSourceContext {
        workspaceSignals = workspaceSignals != null ? List.copyOf(workspaceSignals) : List.of();
        sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
        technicalSignals = technicalSignals != null ? List.copyOf(technicalSignals) : List.of();
        coverage = coverage != null ? List.copyOf(coverage) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
