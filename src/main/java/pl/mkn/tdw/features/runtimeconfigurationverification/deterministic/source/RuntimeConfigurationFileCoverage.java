package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

public record RuntimeConfigurationFileCoverage(
        RuntimeConfigurationFileRole role,
        String path,
        RuntimeConfigurationFileStatus status,
        String commitId,
        String lastCommitId,
        String lastModifiedAt,
        Long sizeBytes,
        String errorCode
) {

    public boolean complete() {
        return status == RuntimeConfigurationFileStatus.AVAILABLE;
    }
}
