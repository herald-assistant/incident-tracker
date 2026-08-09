package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

public record ConfigDriftViewerFileCoverage(
        ConfigDriftViewerFileRole role,
        String path,
        ConfigDriftViewerFileStatus status,
        String commitId,
        String lastCommitId,
        String lastModifiedAt,
        Long sizeBytes,
        String errorCode
) {

    public boolean complete() {
        return status == ConfigDriftViewerFileStatus.AVAILABLE;
    }
}
