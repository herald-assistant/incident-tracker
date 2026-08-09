package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import java.util.List;

public record ConfigDriftViewerBranchCoverage(
        String branch,
        boolean branchExists,
        List<ConfigDriftViewerFileCoverage> files
) {

    public ConfigDriftViewerBranchCoverage {
        files = files != null ? List.copyOf(files) : List.of();
    }

    public boolean complete() {
        return branchExists && files.size() == 3
                && files.stream().allMatch(ConfigDriftViewerFileCoverage::complete);
    }
}
