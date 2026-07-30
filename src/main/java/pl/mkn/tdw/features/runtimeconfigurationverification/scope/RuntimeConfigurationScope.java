package pl.mkn.tdw.features.runtimeconfigurationverification.scope;

public record RuntimeConfigurationScope(
        String repositoryId,
        String connectionId,
        String projectPath,
        String systemId,
        String systemLabel,
        String configurationDirectory
) {
}
