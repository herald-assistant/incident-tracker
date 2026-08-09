package pl.mkn.tdw.features.configdriftviewer.source;

public record ConfigDriftViewerRepositoryProfile(
        String id,
        String displayName,
        String connectionId,
        String projectPath
) {
}
