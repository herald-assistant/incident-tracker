package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import com.fasterxml.jackson.annotation.JsonIgnore;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileMetadata;

final class RuntimeConfigurationRawFile {

    private final RuntimeConfigurationFileRole role;
    private final String path;
    private final String content;
    private final GitLabExactFileMetadata metadata;

    RuntimeConfigurationRawFile(
            RuntimeConfigurationFileRole role,
            String path,
            String content,
            GitLabExactFileMetadata metadata
    ) {
        this.role = role;
        this.path = path;
        this.content = content;
        this.metadata = metadata;
    }

    RuntimeConfigurationFileRole role() {
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
        return "RuntimeConfigurationRawFile[role=" + role
                + ", path=" + path
                + ", content=<redacted>"
                + ", metadata=<redacted>"
                + "]";
    }
}
