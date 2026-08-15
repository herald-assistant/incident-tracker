package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendSourceRevision(
        String ref,
        String commitId
) {
}

