package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ParsedConfigurationSnapshot {

    private final String branch;
    private final Map<ConfigDriftViewerFileRole, ParsedConfigurationFile> files;

    public ParsedConfigurationSnapshot(String branch, List<ParsedConfigurationFile> files) {
        this.branch = branch;
        var values = new EnumMap<ConfigDriftViewerFileRole, ParsedConfigurationFile>(
                ConfigDriftViewerFileRole.class
        );
        if (files != null) {
            files.forEach(file -> values.put(file.role(), file));
        }
        this.files = Map.copyOf(values);
    }

    public String branch() {
        return branch;
    }

    public ParsedConfigurationFile file(ConfigDriftViewerFileRole role) {
        return files.get(role);
    }

    public List<ParsedConfigurationFile> files() {
        return List.copyOf(files.values());
    }

    @Override
    public String toString() {
        return "ParsedConfigurationSnapshot[branch=" + branch
                + ", files=<redacted:" + files.size() + ">]";
    }
}
