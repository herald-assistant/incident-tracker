package pl.mkn.tdw.api.workspacesettings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsFieldResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsGitLabResponse;
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
                .andExpect(jsonPath("$.values.gitLab.group.source").value("WORKSPACE_SETTINGS"));
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
                                  "gitLab": {
                                    "baseUrl": "https://gitlab.example.com",
                                    "group": "platform/backend",
                                    "token": "glpat_secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.gitLab.token.secret").value(true));
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
                        )
                )
        );
    }
}
