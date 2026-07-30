package pl.mkn.tdw.features.runtimeconfigurationverification.source;

public record RuntimeConfigurationRepositoryProfile(
        String id,
        String displayName,
        String connectionId,
        String projectPath
) {
}
