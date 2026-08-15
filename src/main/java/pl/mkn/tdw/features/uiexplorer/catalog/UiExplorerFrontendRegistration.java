package pl.mkn.tdw.features.uiexplorer.catalog;

import java.util.List;

public record UiExplorerFrontendRegistration(
        String systemId,
        String label,
        String summary,
        String repositoryId,
        String projectPath,
        String gitLabGroup,
        String gitLabProjectName,
        String defaultBranch,
        String searchMode,
        List<String> pathPrefixes
) {

    public UiExplorerFrontendRegistration {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
