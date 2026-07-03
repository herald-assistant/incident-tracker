package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceSettingsFile(
        String schema,
        int version,
        LocalWorkspaceAppUiSettings appUi,
        LocalWorkspaceCopilotSettings copilot,
        LocalWorkspaceGitLabSettings gitLab,
        LocalWorkspaceElasticsearchSettings elasticsearch,
        LocalWorkspaceDynatraceSettings dynatrace
) {

    public static final String SCHEMA = "tdw.workspace-settings";
    public static final int VERSION = 4;

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
        if (gitLab == null) {
            gitLab = LocalWorkspaceGitLabSettings.empty();
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
                LocalWorkspaceGitLabSettings.empty(),
                LocalWorkspaceElasticsearchSettings.empty(),
                LocalWorkspaceDynatraceSettings.empty()
        );
    }
}
