package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model;

import java.util.List;

public record SanitizedConfigurationNode(
        String name,
        String path,
        RuntimeConfigurationValueType sourceType,
        RuntimeConfigurationValueType targetType,
        RuntimeConfigurationChangeKind relation,
        RuntimeConfigurationSensitivity sensitivity,
        String sourceValueToken,
        String targetValueToken,
        Integer sourceCardinality,
        Integer targetCardinality,
        List<SanitizedConfigurationNode> children
) {

    public SanitizedConfigurationNode {
        children = children != null ? List.copyOf(children) : List.of();
    }
}
