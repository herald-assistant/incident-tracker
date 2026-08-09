package pl.mkn.tdw.features.configdriftviewer.scope;

public record ConfigDriftViewerScope(
        String repositoryId,
        String connectionId,
        String projectPath,
        String systemId,
        String systemLabel,
        String configurationDirectory
) {
}
