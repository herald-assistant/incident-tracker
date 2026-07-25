package pl.mkn.tdw.localworkspace.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocalWorkspaceJiraSettings(
        String baseUrl,
        String token
) {

    public static LocalWorkspaceJiraSettings empty() {
        return new LocalWorkspaceJiraSettings(null, null);
    }
}
