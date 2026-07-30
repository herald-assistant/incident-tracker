package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import java.util.List;

public record RuntimeConfigurationBranchCoverage(
        String branch,
        boolean branchExists,
        List<RuntimeConfigurationFileCoverage> files
) {

    public RuntimeConfigurationBranchCoverage {
        files = files != null ? List.copyOf(files) : List.of();
    }

    public boolean complete() {
        return branchExists && files.size() == 3
                && files.stream().allMatch(RuntimeConfigurationFileCoverage::complete);
    }
}
