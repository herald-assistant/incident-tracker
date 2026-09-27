package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendSourceRevision(
        String ref,
        String commitId
) {
}

