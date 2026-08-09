package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceConfigDriftViewerGitLabSettings(
        String baseUrl,
        String token
) {

    public static LocalWorkspaceConfigDriftViewerGitLabSettings empty() {
        return new LocalWorkspaceConfigDriftViewerGitLabSettings(null, null);
    }
}
