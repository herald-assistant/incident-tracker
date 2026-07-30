package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

final class RuntimeConfigurationRawSnapshotPair {

    private final RuntimeConfigurationRawSnapshot source;
    private final RuntimeConfigurationRawSnapshot target;

    RuntimeConfigurationRawSnapshotPair(
            RuntimeConfigurationRawSnapshot source,
            RuntimeConfigurationRawSnapshot target
    ) {
        this.source = source;
        this.target = target;
    }

    RuntimeConfigurationRawSnapshot source() {
        return source;
    }

    RuntimeConfigurationRawSnapshot target() {
        return target;
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationRawSnapshotPair[source=<redacted>, target=<redacted>]";
    }
}
