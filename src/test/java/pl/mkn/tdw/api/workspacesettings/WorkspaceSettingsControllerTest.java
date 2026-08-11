package pl.mkn.tdw.api.workspacesettings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsConfluenceResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsCopilotResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsDynatraceResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsElasticsearchResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsFieldResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsGitLabResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsJiraResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsConfigDriftViewerGitLabResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsSource;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsValuesResponse;

@WebMvcTest(WorkspaceSettingsController.class)
class WorkspaceSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceSettingsService workspaceSettingsService;

    @Test
    void shouldExposeWorkspaceSettings() throws Exception {
        when(workspaceSettingsService.currentSettings()).thenReturn(response());

        mockMvc.perform(get("/api/workspace/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingsPath").value("tdw-data/settings.json"))
                .andExpect(jsonPath("$.values.appUi.title.value").value("CRM workspace"))
                .andExpect(jsonPath("$.values.copilot.localGithubToken.value").value("ghu_secret"))
                .andExpect(jsonPath("$.values.jira.baseUrl.value").value("https://jira.example.com"))
                .andExpect(jsonPath("$.values.confluence.baseUrl.value")
                        .value("https://confluence.example.com"))
                .andExpect(jsonPath("$.values.confluence.baseUrl.propertyKey")
                        .value("analysis.confluence.base-url"))
                .andExpect(jsonPath("$.values.confluence.baseUrl.source").value("WORKSPACE_SETTINGS"))
                .andExpect(jsonPath("$.values.confluence.token.propertyKey")
                        .value("analysis.confluence.token"))
                .andExpect(jsonPath("$.values.confluence.token.secret").value(true))
                .andExpect(jsonPath("$.values.gitLab.group.source").value("WORKSPACE_SETTINGS"))
                .andExpect(jsonPath("$.values.configDriftViewerGitLab.baseUrl.value")
                        .value("https://runtime-config.example.com"))
                .andExpect(jsonPath("$.values.elasticsearch.indexPattern.value").value("logs-platform-*"))
                .andExpect(jsonPath("$.values.dynatrace.baseUrl.value").value("https://dynatrace.example.com"));
    }

    @Test
    void shouldSaveWorkspaceSettings() throws Exception {
        when(workspaceSettingsService.saveSettings(any())).thenReturn(response());

        mockMvc.perform(put("/api/workspace/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "appUi": {
                                    "title": "CRM workspace"
                                  },
                                  "copilot": {
                                    "localGithubToken": "ghu_secret"
                                  },
                                  "jira": {
                                    "baseUrl": "https://jira.example.com",
                                    "token": "jira_secret"
                                  },
                                  "confluence": {
                                    "baseUrl": "https://confluence.example.com",
                                    "token": "confluence_secret"
                                  },
                                  "gitLab": {
                                    "baseUrl": "https://gitlab.example.com",
                                    "group": "platform/backend",
                                    "token": "glpat_secret"
                                  },
                                  "configDriftViewerGitLab": {
                                    "baseUrl": "https://runtime-config.example.com",
                                    "token": "glpat_runtime_config_secret"
                                  },
                                  "elasticsearch": {
                                    "baseUrl": "https://elastic.example.com",
                                    "kibanaSpaceId": "default",
                                    "indexPattern": "logs-platform-*",
                                    "authorizationHeader": "Bearer secret"
                                  },
                                  "dynatrace": {
                                    "baseUrl": "https://dynatrace.example.com",
                                    "apiToken": "dt0c01_secret"
                                  }
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.copilot.localGithubToken.secret").value(true))
                .andExpect(jsonPath("$.values.jira.token.secret").value(true))
                .andExpect(jsonPath("$.values.confluence.token.secret").value(true))
                .andExpect(jsonPath("$.values.gitLab.token.secret").value(true))
                .andExpect(jsonPath("$.values.configDriftViewerGitLab.token.secret").value(true))
                .andExpect(jsonPath("$.values.elasticsearch.authorizationHeader.secret").value(true))
                .andExpect(jsonPath("$.values.dynatrace.apiToken.secret").value(true));

        verify(workspaceSettingsService).saveSettings(argThat(request ->
                request.confluence() != null
                        && "https://confluence.example.com".equals(request.confluence().baseUrl())
                        && "confluence_secret".equals(request.confluence().token())
        ));
    }

    private WorkspaceSettingsResponse response() {
        return new WorkspaceSettingsResponse(
                true,
                "tdw-data/settings.json",
                new WorkspaceSettingsValuesResponse(
                        new WorkspaceSettingsAppUiResponse(new WorkspaceSettingsFieldResponse(
                                "app.ui.title",
                                "CRM workspace",
                                "",
                                "CRM workspace",
                                WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                false
                        )),
                        new WorkspaceSettingsCopilotResponse(new WorkspaceSettingsFieldResponse(
                                "analysis.ai.copilot.auth.local.github-token",
                                "ghu_secret",
                                "",
                                "ghu_secret",
                                WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                true
                        )),
                        new WorkspaceSettingsJiraResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.jira.base-url",
                                        "https://jira.example.com",
                                        "https://jira.example.com",
                                        null,
                                        WorkspaceSettingsSource.APPLICATION_PROPERTIES,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.jira.token",
                                        "jira_secret",
                                        "",
                                        "jira_secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        ),
                        new WorkspaceSettingsConfluenceResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.confluence.base-url",
                                        "https://confluence.example.com",
                                        "https://confluence.app",
                                        "https://confluence.example.com",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.confluence.token",
                                        "confluence_secret",
                                        "",
                                        "confluence_secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        ),
                        new WorkspaceSettingsGitLabResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.gitlab.base-url",
                                        "https://gitlab.example.com",
                                        "https://gitlab.example.com",
                                        null,
                                        WorkspaceSettingsSource.APPLICATION_PROPERTIES,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.gitlab.group",
                                        "platform/backend",
                                        "platform/app",
                                        "platform/backend",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.gitlab.token",
                                        "glpat_secret",
                                        "",
                                        "glpat_secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        ),
                        new WorkspaceSettingsConfigDriftViewerGitLabResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "integrations.gitlab.named.connections.runtime-config.base-url",
                                        "https://runtime-config.example.com",
                                        "https://runtime-config.app",
                                        "https://runtime-config.example.com",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "integrations.gitlab.named.connections.runtime-config.token",
                                        "glpat_runtime_config_secret",
                                        "",
                                        "glpat_runtime_config_secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        ),
                        new WorkspaceSettingsElasticsearchResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.elasticsearch.base-url",
                                        "https://elastic.example.com",
                                        "https://elastic.example.com",
                                        null,
                                        WorkspaceSettingsSource.APPLICATION_PROPERTIES,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.elasticsearch.kibana-space-id",
                                        "default",
                                        "default",
                                        null,
                                        WorkspaceSettingsSource.APPLICATION_PROPERTIES,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.elasticsearch.index-pattern",
                                        "logs-platform-*",
                                        "logs-*",
                                        "logs-platform-*",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.elasticsearch.authorization-header",
                                        "Bearer secret",
                                        "",
                                        "Bearer secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        ),
                        new WorkspaceSettingsDynatraceResponse(
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.dynatrace.base-url",
                                        "https://dynatrace.example.com",
                                        "https://dynatrace.app",
                                        "https://dynatrace.example.com",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        false
                                ),
                                new WorkspaceSettingsFieldResponse(
                                        "analysis.dynatrace.api-token",
                                        "dt0c01_secret",
                                        "",
                                        "dt0c01_secret",
                                        WorkspaceSettingsSource.WORKSPACE_SETTINGS,
                                        true
                                )
                        )
                )
        );
    }
}
