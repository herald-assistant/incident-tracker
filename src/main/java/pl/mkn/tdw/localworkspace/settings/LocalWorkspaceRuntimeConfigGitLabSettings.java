package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceRuntimeConfigGitLabSettings(
        String baseUrl,
        String token
) {

    public static LocalWorkspaceRuntimeConfigGitLabSettings empty() {
        return new LocalWorkspaceRuntimeConfigGitLabSettings(null, null);
    }
}
