package pl.mkn.tdw.localworkspace.settings;

public record LocalWorkspaceElasticsearchSettings(
        String baseUrl,
        String kibanaSpaceId,
        String indexPattern,
        String authorizationHeader
) {

    public static LocalWorkspaceElasticsearchSettings empty() {
        return new LocalWorkspaceElasticsearchSettings(null, null, null, null);
    }
}
