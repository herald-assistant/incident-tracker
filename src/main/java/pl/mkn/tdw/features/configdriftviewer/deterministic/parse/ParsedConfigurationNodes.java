package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ParsedConfigurationNodes {

    private ParsedConfigurationNodes() {
    }

    static ParsedConfigurationNode fromObject(String name, String path, Object value) {
        if (value instanceof Map<?, ?> map) {
            var children = new ArrayList<ParsedConfigurationNode>();
            map.forEach((key, childValue) -> {
                var childName = String.valueOf(key);
                children.add(fromObject(childName, childPath(path, childName), childValue));
            });
            return new ParsedConfigurationNode(
                    name,
                    path,
                    ConfigDriftViewerValueType.MAP,
                    null,
                    children
            );
        }
        if (value instanceof List<?> list) {
            var children = new ArrayList<ParsedConfigurationNode>();
            for (var index = 0; index < list.size(); index++) {
                var childName = "[" + index + "]";
                children.add(fromObject(childName, path + childName, list.get(index)));
            }
            return new ParsedConfigurationNode(
                    name,
                    path,
                    ConfigDriftViewerValueType.LIST,
                    null,
                    children
            );
        }
        return new ParsedConfigurationNode(name, path, scalarType(value), value, List.of());
    }

    private static ConfigDriftViewerValueType scalarType(Object value) {
        if (value == null) {
            return ConfigDriftViewerValueType.NULL;
        }
        if (value instanceof Boolean) {
            return ConfigDriftViewerValueType.BOOLEAN;
        }
        if (value instanceof Number
                || value instanceof BigDecimal
                || value instanceof BigInteger) {
            return ConfigDriftViewerValueType.NUMBER;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return ConfigDriftViewerValueType.STRING;
        }
        return ConfigDriftViewerValueType.UNKNOWN;
    }

    private static String childPath(String path, String childName) {
        return path == null || path.isBlank() ? childName : path + "." + childName;
    }
}
