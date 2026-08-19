package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

record GitLabFrontendSemanticContext(
        List<GitLabFrontendSourceManifestEntry> sourceManifest,
        List<GitLabFrontendSourceSlice> sourceSlices,
        List<GitLabFrontendUseCaseRelation> relations,
        List<GitLabFrontendUnresolvedFrontier> unresolvedFrontier,
        GitLabFrontendContextMetrics metrics
) {
    GitLabFrontendSemanticContext {
        sourceManifest = sourceManifest != null ? List.copyOf(sourceManifest) : List.of();
        sourceSlices = sourceSlices != null ? List.copyOf(sourceSlices) : List.of();
        relations = relations != null ? List.copyOf(relations) : List.of();
        unresolvedFrontier = unresolvedFrontier != null ? List.copyOf(unresolvedFrontier) : List.of();
    }
}
