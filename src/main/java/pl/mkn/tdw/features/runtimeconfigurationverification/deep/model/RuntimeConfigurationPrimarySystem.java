package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

import java.util.List;

public record RuntimeConfigurationPrimarySystem(
        String systemId,
        String label,
        String kind,
        String resolvedConfigurationDirectory,
        String configurationDirectoryResolution,
        List<String> codeSearchScopeIds
) {

    public RuntimeConfigurationPrimarySystem {
        codeSearchScopeIds = codeSearchScopeIds != null
                ? List.copyOf(codeSearchScopeIds)
                : List.of();
    }
}
