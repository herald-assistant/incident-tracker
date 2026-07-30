package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

public record RuntimeConfigurationDifference(
        String differenceId,
        RuntimeConfigurationFileRole role,
        int documentIndex,
        String path,
        RuntimeConfigurationChangeKind kind,
        RuntimeConfigurationValueType sourceType,
        RuntimeConfigurationValueType targetType,
        RuntimeConfigurationSensitivity sensitivity,
        String sourceValueToken,
        String targetValueToken
) {
}
