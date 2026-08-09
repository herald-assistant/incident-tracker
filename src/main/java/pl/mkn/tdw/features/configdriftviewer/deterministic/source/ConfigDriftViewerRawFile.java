package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import com.fasterxml.jackson.annotation.JsonIgnore;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileMetadata;

final class ConfigDriftViewerRawFile {

    private final ConfigDriftViewerFileRole role;
    private final String path;
    private final String content;
    private final GitLabExactFileMetadata metadata;

    ConfigDriftViewerRawFile(
            ConfigDriftViewerFileRole role,
            String path,
            String content,
            GitLabExactFileMetadata metadata
    ) {
        this.role = role;
        this.path = path;
        this.content = content;
        this.metadata = metadata;
    }

    ConfigDriftViewerFileRole role() {
        return role;
    }

    String path() {
        return path;
    }

    @JsonIgnore
    String content() {
        return content;
    }

    GitLabExactFileMetadata metadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerRawFile[role=" + role
                + ", path=" + path
                + ", content=<redacted>"
                + ", metadata=<redacted>"
                + "]";
    }
}
