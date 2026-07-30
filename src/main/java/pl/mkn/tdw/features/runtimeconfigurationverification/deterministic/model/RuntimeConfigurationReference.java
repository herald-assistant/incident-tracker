package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

public record RuntimeConfigurationReference(
        String referenceId,
        RuntimeConfigurationFileRole sourceRole,
        int documentIndex,
        String sourcePath,
        String targetPath,
        String referenceKind,
        RuntimeConfigurationReferenceStatus sourceStatus,
        RuntimeConfigurationReferenceStatus targetStatus
) {
}
