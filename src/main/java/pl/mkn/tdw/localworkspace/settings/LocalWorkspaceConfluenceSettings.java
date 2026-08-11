package pl.mkn.tdw.localworkspace.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocalWorkspaceConfluenceSettings(
        String baseUrl,
        String token
) {

    public static LocalWorkspaceConfluenceSettings empty() {
        return new LocalWorkspaceConfluenceSettings(null, null);
    }
}
