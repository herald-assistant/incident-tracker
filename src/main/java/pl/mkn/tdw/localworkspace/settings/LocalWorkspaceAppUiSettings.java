package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceAppUiSettings(
        String title
) {

    public static LocalWorkspaceAppUiSettings empty() {
        return new LocalWorkspaceAppUiSettings(null);
    }
}
