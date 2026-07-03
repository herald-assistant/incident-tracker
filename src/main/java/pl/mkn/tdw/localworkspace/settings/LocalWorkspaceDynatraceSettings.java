package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceDynatraceSettings(
        String baseUrl,
        String apiToken
) {

    public static LocalWorkspaceDynatraceSettings empty() {
        return new LocalWorkspaceDynatraceSettings(null, null);
    }
}
