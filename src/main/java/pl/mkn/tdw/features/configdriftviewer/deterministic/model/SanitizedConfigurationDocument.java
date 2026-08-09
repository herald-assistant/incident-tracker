package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

public record SanitizedConfigurationDocument(
        ConfigDriftViewerFileRole role,
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
