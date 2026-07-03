package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceGitLabSettings(
        String baseUrl,
        String group,
        String token
) {

    public static LocalWorkspaceGitLabSettings empty() {
        return new LocalWorkspaceGitLabSettings(null, null, null);
    }
}
