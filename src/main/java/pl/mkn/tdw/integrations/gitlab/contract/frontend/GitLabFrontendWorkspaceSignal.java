package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendWorkspaceSignal(
        String kind,
        String value,
        String sourcePath
) {
}

