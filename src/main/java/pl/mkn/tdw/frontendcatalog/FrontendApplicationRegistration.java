package pl.mkn.tdw.frontendcatalog;

import java.util.List;

public record FrontendApplicationRegistration(
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
    public FrontendApplicationRegistration {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}

