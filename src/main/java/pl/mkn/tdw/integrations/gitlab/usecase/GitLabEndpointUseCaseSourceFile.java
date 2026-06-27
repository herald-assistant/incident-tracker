package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseSourceFile(
        String path,
        String content,
        boolean truncated,
        boolean readSuccessful,
        List<String> limitations
) {
    public GitLabEndpointUseCaseSourceFile {
        path = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        content = content != null ? content : "";
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }
}
