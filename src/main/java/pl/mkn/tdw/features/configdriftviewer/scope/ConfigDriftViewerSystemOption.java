package pl.mkn.tdw.features.configdriftviewer.scope;

public record ConfigDriftViewerSystemOption(
        String id,
        String label,
        String configurationDirectory
) {
}
