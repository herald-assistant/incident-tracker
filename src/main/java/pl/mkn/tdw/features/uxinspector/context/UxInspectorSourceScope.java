package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.common.GitLabPathUtils;

import java.util.List;

public record UxInspectorSourceScope(String mainGroup, String group, String projectName, String ref, List<String> pathPrefixes) {
    public UxInspectorSourceScope {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }

    public String projectInMainGroup() {
        return GitLabPathUtils.relativeProjectPath(mainGroup, group + "/" + projectName);
    }
}

