package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceSettingsFile(
        String schema,
        int version,
        LocalWorkspaceAppUiSettings appUi,
        LocalWorkspaceGitLabSettings gitLab
) {

    public static final String SCHEMA = "tdw.workspace-settings";
    public static final int VERSION = 1;

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
        if (gitLab == null) {
            gitLab = LocalWorkspaceGitLabSettings.empty();
        }
    }

    public static LocalWorkspaceSettingsFile empty() {
        return new LocalWorkspaceSettingsFile(
                SCHEMA,
                VERSION,
                LocalWorkspaceAppUiSettings.empty(),
                LocalWorkspaceGitLabSettings.empty()
        );
    }
}
