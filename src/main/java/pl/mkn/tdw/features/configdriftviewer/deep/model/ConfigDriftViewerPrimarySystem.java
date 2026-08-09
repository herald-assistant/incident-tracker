package pl.mkn.tdw.features.configdriftviewer.deep.model;

import java.util.List;

public record ConfigDriftViewerPrimarySystem(
        String systemId,
        String label,
        String kind,
        String resolvedConfigurationDirectory,
        String configurationDirectoryResolution,
        List<String> codeSearchScopeIds
) {

    public ConfigDriftViewerPrimarySystem {
        codeSearchScopeIds = codeSearchScopeIds != null
                ? List.copyOf(codeSearchScopeIds)
                : List.of();
    }
}
