package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

public record SanitizedConfigurationDocument(
        RuntimeConfigurationFileRole role,
        String sourcePath,
        String targetPath,
        int documentIndex,
        boolean sourcePresent,
        boolean targetPresent,
        String sourceProfileToken,
        String targetProfileToken,
        SanitizedConfigurationNode root
) {
}
