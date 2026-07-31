package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.util.List;
import java.util.Objects;

public record RuntimeConfigurationDiffFile(
        RuntimeConfigurationFileRole role,
        RuntimeConfigurationDiffFileFormat format,
        String sourcePath,
        String targetPath,
        boolean sourcePresent,
        boolean targetPresent,
        List<RuntimeConfigurationDiffDocument> documents
) {

    public RuntimeConfigurationDiffFile {
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(format, "format is required");
        documents = documents != null ? List.copyOf(documents) : List.of();
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDiffFile[role=" + role
                + ", format=" + format
                + ", sourcePath=<redacted>"
                + ", targetPath=<redacted>"
                + ", sourcePresent=" + sourcePresent
                + ", targetPresent=" + targetPresent
                + ", documents=<redacted:" + documents.size() + ">"
                + "]";
    }
}
