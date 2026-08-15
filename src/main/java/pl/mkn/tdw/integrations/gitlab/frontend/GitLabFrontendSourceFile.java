package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendSourceFile(
        String path,
        List<GitLabFrontendSourceRole> roles,
        String content,
        int returnedCharacters,
        boolean truncated
) {

    public GitLabFrontendSourceFile {
        roles = roles != null ? List.copyOf(roles) : List.of();
        content = content != null ? content : "";
    }
}

