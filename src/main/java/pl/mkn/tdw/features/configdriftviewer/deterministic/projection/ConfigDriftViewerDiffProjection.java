package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import java.util.List;

public record ConfigDriftViewerDiffProjection(
        String sourceBranch,
        String targetBranch,
        List<ConfigDriftViewerDiffFile> files
) {

    public ConfigDriftViewerDiffProjection {
        files = files != null ? List.copyOf(files) : List.of();
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDiffProjection[sourceBranch=" + sourceBranch
                + ", targetBranch=" + targetBranch
                + ", files=<redacted:" + files.size() + ">"
                + "]";
    }
}
