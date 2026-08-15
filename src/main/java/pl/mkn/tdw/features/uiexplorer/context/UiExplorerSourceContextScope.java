package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerSourceContextScope(
        String gitLabGroup,
        String projectName,
        String ref,
        List<String> pathPrefixes
) {

    public UiExplorerSourceContextScope {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
