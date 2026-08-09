package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;

import java.util.List;

public final class ParsedConfigurationNode {

    private final String name;
    private final String path;
    private final ConfigDriftViewerValueType type;
    private final Object scalarValue;
    private final List<ParsedConfigurationNode> children;

    public ParsedConfigurationNode(
            String name,
            String path,
            ConfigDriftViewerValueType type,
            Object scalarValue,
            List<ParsedConfigurationNode> children
    ) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.scalarValue = scalarValue;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public ConfigDriftViewerValueType type() {
        return type;
    }

    @JsonIgnore
    public Object scalarValue() {
        return scalarValue;
    }

    public List<ParsedConfigurationNode> children() {
        return children;
    }

    public boolean scalar() {
        return switch (type) {
            case STRING, NUMBER, BOOLEAN, NULL, UNKNOWN -> true;
            case MAP, LIST -> false;
        };
    }

    @Override
    public String toString() {
        return "ParsedConfigurationNode[name=" + name
                + ", path=" + path
                + ", type=" + type
                + ", scalarValue=<redacted>"
                + ", children=" + children.size()
                + "]";
    }
}
