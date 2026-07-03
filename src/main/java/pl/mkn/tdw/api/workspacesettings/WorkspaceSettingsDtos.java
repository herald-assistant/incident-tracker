package pl.mkn.tdw.api.workspacesettings;

public final class WorkspaceSettingsDtos {

    private WorkspaceSettingsDtos() {
    }

    public enum WorkspaceSettingsSource {
        APPLICATION_PROPERTIES,
        WORKSPACE_SETTINGS
    }

    public record WorkspaceSettingsResponse(
            boolean workspaceEnabled,
            String settingsPath,
            WorkspaceSettingsValuesResponse values
    ) {
    }

    public record WorkspaceSettingsValuesResponse(
            WorkspaceSettingsAppUiResponse appUi,
            WorkspaceSettingsCopilotResponse copilot,
            WorkspaceSettingsGitLabResponse gitLab,
            WorkspaceSettingsElasticsearchResponse elasticsearch,
            WorkspaceSettingsDynatraceResponse dynatrace
    ) {
    }

    public record WorkspaceSettingsAppUiResponse(
            WorkspaceSettingsFieldResponse title
    ) {
    }

    public record WorkspaceSettingsCopilotResponse(
            WorkspaceSettingsFieldResponse localGithubToken
    ) {
    }

    public record WorkspaceSettingsGitLabResponse(
            WorkspaceSettingsFieldResponse baseUrl,
            WorkspaceSettingsFieldResponse group,
            WorkspaceSettingsFieldResponse token
    ) {
    }

    public record WorkspaceSettingsElasticsearchResponse(
            WorkspaceSettingsFieldResponse baseUrl,
            WorkspaceSettingsFieldResponse kibanaSpaceId,
            WorkspaceSettingsFieldResponse indexPattern,
            WorkspaceSettingsFieldResponse authorizationHeader
    ) {
    }

    public record WorkspaceSettingsDynatraceResponse(
            WorkspaceSettingsFieldResponse baseUrl,
            WorkspaceSettingsFieldResponse apiToken
    ) {
    }

    public record WorkspaceSettingsFieldResponse(
            String propertyKey,
            String value,
            String applicationValue,
            String workspaceValue,
            WorkspaceSettingsSource source,
            boolean secret
    ) {
    }

    public record WorkspaceSettingsUpdateRequest(
            WorkspaceSettingsAppUiUpdate appUi,
            WorkspaceSettingsCopilotUpdate copilot,
            WorkspaceSettingsGitLabUpdate gitLab,
            WorkspaceSettingsElasticsearchUpdate elasticsearch,
            WorkspaceSettingsDynatraceUpdate dynatrace
    ) {
    }

    public record WorkspaceSettingsAppUiUpdate(
            String title
    ) {
    }

    public record WorkspaceSettingsCopilotUpdate(
            String localGithubToken
    ) {
    }

    public record WorkspaceSettingsGitLabUpdate(
            String baseUrl,
            String group,
            String token
    ) {
    }

    public record WorkspaceSettingsElasticsearchUpdate(
            String baseUrl,
            String kibanaSpaceId,
            String indexPattern,
            String authorizationHeader
    ) {
    }

    public record WorkspaceSettingsDynatraceUpdate(
            String baseUrl,
            String apiToken
    ) {
    }
}
