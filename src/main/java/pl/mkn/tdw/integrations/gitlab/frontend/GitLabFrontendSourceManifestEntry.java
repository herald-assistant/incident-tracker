package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendSourceManifestEntry(
        String path,
        List<GitLabFrontendSourceRole> roles,
        int sourceCharacters,
        String contentSha256,
        int sliceCount
) {
    public GitLabFrontendSourceManifestEntry {
        roles = roles != null ? List.copyOf(roles) : List.of();
    }
}
