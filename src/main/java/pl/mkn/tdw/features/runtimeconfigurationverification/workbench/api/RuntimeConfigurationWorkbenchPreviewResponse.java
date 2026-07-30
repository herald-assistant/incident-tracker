package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.time.Instant;
import java.util.List;

public record RuntimeConfigurationWorkbenchPreviewResponse(
        String previewId,
        Instant expiresAt,
        RuntimeConfigurationVerificationMode mode,
        String repositoryId,
        String systemId,
        String sourceBranch,
        String targetBranch,
        String codeRef,
        SourceSummary source,
        Counts counts,
        AnonymizationSummary anonymization,
        DeepSummary deep,
        List<ArtifactSummary> artifacts,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationWorkbenchPreviewResponse {
        artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public record SourceSummary(
            String configurationDirectory,
            boolean sourceBranchExists,
            boolean sourceComplete,
            boolean targetBranchExists,
            boolean targetComplete
    ) {
    }

    public record Counts(
            int documents,
            int nodes,
            int differences,
            int findings,
            int references
    ) {
    }

    public record AnonymizationSummary(
            int totalNodes,
            int pseudonymizedRepresentations,
            int suppressedRepresentations,
            int structureOnlyRepresentations,
            int notPresentRepresentations
    ) {
    }

    public record DeepSummary(
            boolean requested,
            String status,
            String preflightStatus,
            int repositoryScopes,
            int blockers,
            int codeGroundings,
            int primaryOwners
    ) {
    }

    public record ArtifactSummary(
            String name,
            String mediaType,
            int characterCount,
            boolean truncated
    ) {
    }
}
