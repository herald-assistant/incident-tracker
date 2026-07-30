package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeConfigurationWorkbenchPreviewResponse(
        RuntimeConfigurationVerificationMode mode,
        String repositoryId,
        String systemId,
        String sourceBranch,
        String targetBranch,
        String codeRef,
        SourceAcquisition sourceAcquisition,
        RuntimeConfigurationDeterministicContext mapping,
        AnonymizationSummary anonymization,
        RuntimeConfigurationDeepContext deepContext,
        String preparedPrompt,
        Map<String, String> artifactContents,
        List<ArtifactSummary> artifacts,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationWorkbenchPreviewResponse {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public record SourceAcquisition(
            String configurationDirectory,
            RuntimeConfigurationBranchCoverage source,
            RuntimeConfigurationBranchCoverage target
    ) {
    }

    public record AnonymizationSummary(
            int totalNodes,
            int pseudonymizedRepresentations,
            int suppressedRepresentations,
            int structureOnlyRepresentations,
            int notPresentRepresentations,
            List<AnonymizationDecision> decisions
    ) {

        public AnonymizationSummary {
            decisions = decisions != null ? List.copyOf(decisions) : List.of();
        }
    }

    public record AnonymizationDecision(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            String path,
            RuntimeConfigurationChangeKind relation,
            RuntimeConfigurationSensitivity sensitivity,
            RuntimeConfigurationValueType sourceType,
            RuntimeConfigurationValueType targetType,
            ValueRepresentation sourceRepresentation,
            ValueRepresentation targetRepresentation,
            String sourceValueToken,
            String targetValueToken
    ) {
    }

    public enum ValueRepresentation {
        PSEUDONYMIZED,
        SUPPRESSED,
        STRUCTURE_ONLY,
        NOT_PRESENT
    }

    public record ArtifactSummary(
            String name,
            int characterCount,
            boolean truncated
    ) {
    }
}
