package pl.mkn.tdw.features.uxinspector.context;

import java.util.List;

public record UxInspectorSourceScope(String group, String projectName, String ref, List<String> pathPrefixes) {
    public UxInspectorSourceScope {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}

