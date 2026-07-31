package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import java.util.List;

public record RuntimeConfigurationDiffProjection(
        String sourceBranch,
        String targetBranch,
        List<RuntimeConfigurationDiffFile> files
) {

    public RuntimeConfigurationDiffProjection {
        files = files != null ? List.copyOf(files) : List.of();
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDiffProjection[sourceBranch=" + sourceBranch
                + ", targetBranch=" + targetBranch
                + ", files=<redacted:" + files.size() + ">"
                + "]";
    }
}
