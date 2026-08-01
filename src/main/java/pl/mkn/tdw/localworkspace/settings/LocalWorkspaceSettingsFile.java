package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceSettingsFile(
        String schema,
        int version,
        LocalWorkspaceAppUiSettings appUi,
        LocalWorkspaceCopilotSettings copilot,
        LocalWorkspaceJiraSettings jira,
        LocalWorkspaceGitLabSettings gitLab,
        LocalWorkspaceRuntimeConfigGitLabSettings runtimeConfigGitLab,
        LocalWorkspaceElasticsearchSettings elasticsearch,
        LocalWorkspaceDynatraceSettings dynatrace
) {

    public static final String SCHEMA = "tdw.workspace-settings";
    public static final int VERSION = 6;

    public LocalWorkspaceSettingsFile {
        if (schema == null || schema.isBlank()) {
            schema = SCHEMA;
        }
        if (version <= 0) {
            version = VERSION;
        }
        if (appUi == null) {
            appUi = LocalWorkspaceAppUiSettings.empty();
        }
        if (copilot == null) {
            copilot = LocalWorkspaceCopilotSettings.empty();
        }
        if (jira == null) {
            jira = LocalWorkspaceJiraSettings.empty();
        }
        if (gitLab == null) {
            gitLab = LocalWorkspaceGitLabSettings.empty();
        }
        if (runtimeConfigGitLab == null) {
            runtimeConfigGitLab = LocalWorkspaceRuntimeConfigGitLabSettings.empty();
        }
        if (elasticsearch == null) {
            elasticsearch = LocalWorkspaceElasticsearchSettings.empty();
        }
        if (dynatrace == null) {
            dynatrace = LocalWorkspaceDynatraceSettings.empty();
        }
    }

    public static LocalWorkspaceSettingsFile empty() {
        return new LocalWorkspaceSettingsFile(
                SCHEMA,
                VERSION,
                LocalWorkspaceAppUiSettings.empty(),
                LocalWorkspaceCopilotSettings.empty(),
                LocalWorkspaceJiraSettings.empty(),
                LocalWorkspaceGitLabSettings.empty(),
                LocalWorkspaceRuntimeConfigGitLabSettings.empty(),
                LocalWorkspaceElasticsearchSettings.empty(),
                LocalWorkspaceDynatraceSettings.empty()
        );
    }
}
