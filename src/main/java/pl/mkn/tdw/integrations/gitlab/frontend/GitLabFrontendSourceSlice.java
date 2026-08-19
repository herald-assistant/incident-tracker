package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendSourceSlice(
        String sliceId,
        String path,
        List<GitLabFrontendSourceRole> roles,
        GitLabFrontendSourceSliceKind kind,
        String symbol,
        int startLine,
        int endLine,
        String content,
        int returnedCharacters,
        String contentSha256
) {
    public GitLabFrontendSourceSlice {
        roles = roles != null ? List.copyOf(roles) : List.of();
        content = content != null ? content : "";
    }
}
