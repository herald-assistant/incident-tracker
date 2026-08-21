package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerSourceScope(
        String gitLabGroup,
        String projectName,
        String ref,
        List<String> pathPrefixes
) {
    public UiExplorerSourceScope {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
