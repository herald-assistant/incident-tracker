package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;

import java.util.List;
import java.util.Objects;

public record RuntimeConfigurationDiffNode(
        String name,
        String path,
        RuntimeConfigurationChangeKind changeKind,
        RuntimeConfigurationDiffValue source,
        RuntimeConfigurationDiffValue target,
        RuntimeConfigurationDiffValue sourceEffective,
        RuntimeConfigurationDiffValue targetEffective,
        List<String> differenceIds,
        List<RuntimeConfigurationDiffNode> children
) {

    public RuntimeConfigurationDiffNode(
            String name,
            String path,
            RuntimeConfigurationChangeKind changeKind,
            RuntimeConfigurationDiffValue source,
            RuntimeConfigurationDiffValue target,
            List<String> differenceIds,
            List<RuntimeConfigurationDiffNode> children
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

    public RuntimeConfigurationDiffNode {
        Objects.requireNonNull(changeKind, "changeKind is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDiffNode[name=<redacted>"
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
