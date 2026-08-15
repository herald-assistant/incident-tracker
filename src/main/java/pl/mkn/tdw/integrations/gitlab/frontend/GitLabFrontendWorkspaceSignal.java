package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendWorkspaceSignal(
        String kind,
        String value,
        String sourcePath
) {
}

