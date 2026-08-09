package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerBranchCoverage;

import java.util.List;

public record ConfigDriftViewerDeterministicContext(
        String repositoryId,
        String systemId,
        String systemLabel,
        String configurationDirectory,
        String sourceBranch,
        String targetBranch,
        ConfigDriftViewerDeterministicStatus status,
        ConfigDriftViewerBranchCoverage sourceCoverage,
        ConfigDriftViewerBranchCoverage targetCoverage,
        List<SanitizedConfigurationDocument> documents,
        List<ConfigDriftViewerReference> references,
        List<ConfigDriftViewerDifference> differences,
        List<ConfigDriftViewerFinding> findings
) {

    public ConfigDriftViewerDeterministicContext {
        documents = documents != null ? List.copyOf(documents) : List.of();
        references = references != null ? List.copyOf(references) : List.of();
        differences = differences != null ? List.copyOf(differences) : List.of();
        findings = findings != null ? List.copyOf(findings) : List.of();
    }
}
