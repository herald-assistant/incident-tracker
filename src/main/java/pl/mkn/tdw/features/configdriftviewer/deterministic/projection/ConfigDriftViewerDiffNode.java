package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;

import java.util.List;
import java.util.Objects;

public record ConfigDriftViewerDiffNode(
        String name,
        String path,
        ConfigDriftViewerChangeKind changeKind,
        ConfigDriftViewerDiffValue source,
        ConfigDriftViewerDiffValue target,
        ConfigDriftViewerDiffValue sourceEffective,
        ConfigDriftViewerDiffValue targetEffective,
        List<String> differenceIds,
        List<ConfigDriftViewerDiffNode> children
) {

    public ConfigDriftViewerDiffNode(
            String name,
            String path,
            ConfigDriftViewerChangeKind changeKind,
            ConfigDriftViewerDiffValue source,
            ConfigDriftViewerDiffValue target,
            List<String> differenceIds,
            List<ConfigDriftViewerDiffNode> children
    ) {
        this(
                name,
                path,
                changeKind,
                source,
                target,
                null,
                null,
                differenceIds,
                children
        );
    }

    public ConfigDriftViewerDiffNode {
        Objects.requireNonNull(changeKind, "changeKind is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDiffNode[name=<redacted>"
                + ", path=<redacted>"
                + ", changeKind=" + changeKind
                + ", source=<redacted>"
                + ", target=<redacted>"
                + ", sourceEffective=<redacted>"
                + ", targetEffective=<redacted>"
                + ", differenceIds=" + differenceIds
                + ", children=<redacted:" + children.size() + ">"
                + "]";
    }
}
