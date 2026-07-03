package pl.mkn.tdw.api.workspacesettings;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.api.uiconfig.UiConfigProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsFile;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsStore;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsGitLabUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsSource;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsUpdateRequest;

class WorkspaceSettingsServiceTest {

    @Test
    void shouldExposeApplicationPropertiesWhenWorkspaceSettingsAreEmpty() {
        var fixture = fixture();

        fixture.service.initialize();

        var response = fixture.service.currentSettings();

        assertThat(response.values().appUi().title().value()).isEqualTo("App workspace");
        assertThat(response.values().appUi().title().source()).isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().gitLab().baseUrl().value()).isEqualTo("https://gitlab.app");
        assertThat(response.values().gitLab().group().value()).isEqualTo("app/group");
        assertThat(response.values().gitLab().token().value()).isEqualTo("app-token");
        assertThat(fixture.gitLabProperties.getBaseUrl()).isEqualTo("https://gitlab.app");
    }

    @Test
    void shouldSaveOnlyValuesDifferentFromApplicationPropertiesAndApplyEffectiveProperties() {
        var fixture = fixture();
        fixture.service.initialize();

        var response = fixture.service.saveSettings(new WorkspaceSettingsUpdateRequest(
                new WorkspaceSettingsAppUiUpdate("Workspace override"),
                new WorkspaceSettingsGitLabUpdate(
                        "https://gitlab.app",
                        "workspace/group",
                        "workspace-token"
                )
        ));

        assertThat(fixture.store.saved.gitLab().baseUrl()).isNull();
        assertThat(fixture.store.saved.gitLab().group()).isEqualTo("workspace/group");
        assertThat(fixture.store.saved.gitLab().token()).isEqualTo("workspace-token");
        assertThat(response.values().gitLab().baseUrl().source()).isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().gitLab().group().source()).isEqualTo(WorkspaceSettingsSource.WORKSPACE_SETTINGS);
        assertThat(fixture.uiConfigProperties.getTitle()).isEqualTo("Workspace override");
        assertThat(fixture.gitLabProperties.getBaseUrl()).isEqualTo("https://gitlab.app");
        assertThat(fixture.gitLabProperties.getGroup()).isEqualTo("workspace/group");
        assertThat(fixture.gitLabProperties.getToken()).isEqualTo("workspace-token");
    }

    @Test
    void shouldClearOverrideWhenSavedValueMatchesApplicationProperties() {
        var fixture = fixture();
        fixture.service.initialize();
        fixture.service.saveSettings(new WorkspaceSettingsUpdateRequest(
                new WorkspaceSettingsAppUiUpdate("Workspace override"),
                new WorkspaceSettingsGitLabUpdate("https://gitlab.workspace", "workspace/group", "workspace-token")
        ));

        var response = fixture.service.saveSettings(new WorkspaceSettingsUpdateRequest(
                new WorkspaceSettingsAppUiUpdate("App workspace"),
                new WorkspaceSettingsGitLabUpdate("https://gitlab.app", "app/group", "app-token")
        ));

        assertThat(fixture.store.saved.appUi().title()).isNull();
        assertThat(fixture.store.saved.gitLab().baseUrl()).isNull();
        assertThat(fixture.store.saved.gitLab().group()).isNull();
        assertThat(fixture.store.saved.gitLab().token()).isNull();
        assertThat(response.values().appUi().title().source()).isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(fixture.uiConfigProperties.getTitle()).isEqualTo("App workspace");
        assertThat(fixture.gitLabProperties.getGroup()).isEqualTo("app/group");
    }

    private Fixture fixture() {
        var uiConfigProperties = new UiConfigProperties();
        uiConfigProperties.setTitle("App workspace");
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setBaseUrl("https://gitlab.app");
        gitLabProperties.setGroup("app/group");
        gitLabProperties.setToken("app-token");
        var store = new InMemoryLocalWorkspaceSettingsStore();
        return new Fixture(
                uiConfigProperties,
                gitLabProperties,
                store,
                new WorkspaceSettingsService(store, uiConfigProperties, gitLabProperties)
        );
    }

    private record Fixture(
            UiConfigProperties uiConfigProperties,
            GitLabProperties gitLabProperties,
            InMemoryLocalWorkspaceSettingsStore store,
            WorkspaceSettingsService service
    ) {
    }

    private static final class InMemoryLocalWorkspaceSettingsStore implements LocalWorkspaceSettingsStore {

        private LocalWorkspaceSettingsFile saved = LocalWorkspaceSettingsFile.empty();

        @Override
        public LocalWorkspaceSettingsFile read() {
            return saved;
        }

        @Override
        public LocalWorkspaceSettingsFile save(LocalWorkspaceSettingsFile settings) {
            saved = settings;
            return saved;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Path settingsPath() {
            return Path.of("tdw-data/settings.json");
        }
    }
}
