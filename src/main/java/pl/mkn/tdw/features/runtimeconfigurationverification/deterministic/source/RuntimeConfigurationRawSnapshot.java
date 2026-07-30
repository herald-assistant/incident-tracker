package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class RuntimeConfigurationRawSnapshot {

    private final String branch;
    private final RuntimeConfigurationBranchCoverage coverage;
    private final Map<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile> files;

    RuntimeConfigurationRawSnapshot(
            String branch,
            RuntimeConfigurationBranchCoverage coverage,
            Map<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile> files
    ) {
        this.branch = branch;
        this.coverage = coverage;
        var copy = new EnumMap<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile>(
                RuntimeConfigurationFileRole.class
        );
        if (files != null) {
            copy.putAll(files);
        }
        this.files = Map.copyOf(copy);
    }

    String branch() {
        return branch;
    }

    RuntimeConfigurationBranchCoverage coverage() {
        return coverage;
    }

    @JsonIgnore
    RuntimeConfigurationRawFile file(RuntimeConfigurationFileRole role) {
        return files.get(role);
    }

    @JsonIgnore
    List<RuntimeConfigurationRawFile> files() {
        return List.copyOf(files.values());
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationRawSnapshot[branch=" + branch
                + ", coverage=" + coverage
                + ", files=<redacted:" + files.size() + ">]";
    }
}
