package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;

import java.util.List;

public record RuntimeConfigurationDeterministicContext(
        String repositoryId,
        String systemId,
        String systemLabel,
        String configurationDirectory,
        String sourceBranch,
        String targetBranch,
        RuntimeConfigurationDeterministicStatus status,
        RuntimeConfigurationBranchCoverage sourceCoverage,
        RuntimeConfigurationBranchCoverage targetCoverage,
        List<SanitizedConfigurationDocument> documents,
        List<RuntimeConfigurationReference> references,
        List<RuntimeConfigurationDifference> differences,
        List<RuntimeConfigurationFinding> findings
) {

    public RuntimeConfigurationDeterministicContext {
        documents = documents != null ? List.copyOf(documents) : List.of();
        references = references != null ? List.copyOf(references) : List.of();
        differences = differences != null ? List.copyOf(differences) : List.of();
        findings = findings != null ? List.copyOf(findings) : List.of();
    }
}
