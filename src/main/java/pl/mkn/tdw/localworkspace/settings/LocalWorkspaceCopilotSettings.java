package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceCopilotSettings(
        String localGithubToken
) {

    public static LocalWorkspaceCopilotSettings empty() {
        return new LocalWorkspaceCopilotSettings(null);
    }
}
