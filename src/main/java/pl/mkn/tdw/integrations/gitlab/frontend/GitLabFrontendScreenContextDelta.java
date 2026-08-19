package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendScreenContextDelta(
        GitLabFrontendRepositoryScope scope,
        GitLabFrontendSourceRevision sourceRevision,
        String frontierId,
        List<GitLabFrontendSourceManifestEntry> sourceManifest,
        List<GitLabFrontendSourceSlice> sourceSlices,
        List<GitLabFrontendUseCaseRelation> relations,
        List<GitLabFrontendUnresolvedFrontier> unresolvedFrontier,
        List<GitLabFrontendGraphDiagnostic> diagnostics,
        GitLabFrontendContextMetrics metrics,
        boolean contextLimitReached
) {
    public GitLabFrontendScreenContextDelta {
        sourceManifest = sourceManifest != null ? List.copyOf(sourceManifest) : List.of();
        sourceSlices = sourceSlices != null ? List.copyOf(sourceSlices) : List.of();
        relations = relations != null ? List.copyOf(relations) : List.of();
        unresolvedFrontier = unresolvedFrontier != null ? List.copyOf(unresolvedFrontier) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
