package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.util.List;
import java.util.Objects;

public record ConfigDriftViewerDiffFile(
        ConfigDriftViewerFileRole role,
        ConfigDriftViewerDiffFileFormat format,
        String sourcePath,
        String targetPath,
        boolean sourcePresent,
        boolean targetPresent,
        List<ConfigDriftViewerDiffDocument> documents
) {

    public ConfigDriftViewerDiffFile {
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(format, "format is required");
        documents = documents != null ? List.copyOf(documents) : List.of();
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDiffFile[role=" + role
                + ", format=" + format
                + ", sourcePath=<redacted>"
                + ", targetPath=<redacted>"
                + ", sourcePresent=" + sourcePresent
                + ", targetPresent=" + targetPresent
                + ", documents=<redacted:" + documents.size() + ">"
                + "]";
    }
}
