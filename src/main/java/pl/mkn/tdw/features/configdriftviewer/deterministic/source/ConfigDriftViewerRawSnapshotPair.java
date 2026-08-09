package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

final class ConfigDriftViewerRawSnapshotPair {

    private final ConfigDriftViewerRawSnapshot source;
    private final ConfigDriftViewerRawSnapshot target;

    ConfigDriftViewerRawSnapshotPair(
            ConfigDriftViewerRawSnapshot source,
            ConfigDriftViewerRawSnapshot target
    ) {
        this.source = source;
        this.target = target;
    }

    ConfigDriftViewerRawSnapshot source() {
        return source;
    }

    ConfigDriftViewerRawSnapshot target() {
        return target;
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerRawSnapshotPair[source=<redacted>, target=<redacted>]";
    }
}
