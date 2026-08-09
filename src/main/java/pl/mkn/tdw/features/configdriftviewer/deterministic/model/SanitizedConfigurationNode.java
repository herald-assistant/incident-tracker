package pl.mkn.tdw.features.configdriftviewer.deterministic.model;

import java.util.List;

public record SanitizedConfigurationNode(
        String name,
        String path,
        ConfigDriftViewerValueType sourceType,
        ConfigDriftViewerValueType targetType,
        ConfigDriftViewerChangeKind relation,
        ConfigDriftViewerSensitivity sensitivity,
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
