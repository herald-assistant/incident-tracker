package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ConfigDriftViewerRawSnapshot {

    private final String branch;
    private final ConfigDriftViewerBranchCoverage coverage;
    private final Map<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile> files;

    ConfigDriftViewerRawSnapshot(
            String branch,
            ConfigDriftViewerBranchCoverage coverage,
            Map<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile> files
    ) {
        this.branch = branch;
        this.coverage = coverage;
        var copy = new EnumMap<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile>(
                ConfigDriftViewerFileRole.class
        );
        if (files != null) {
            copy.putAll(files);
        }
        this.files = Map.copyOf(copy);
    }

    String branch() {
        return branch;
    }

    ConfigDriftViewerBranchCoverage coverage() {
        return coverage;
    }

    @JsonIgnore
    ConfigDriftViewerRawFile file(ConfigDriftViewerFileRole role) {
        return files.get(role);
    }

    @JsonIgnore
    List<ConfigDriftViewerRawFile> files() {
        return List.copyOf(files.values());
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerRawSnapshot[branch=" + branch
                + ", coverage=" + coverage
                + ", files=<redacted:" + files.size() + ">]";
    }
}
