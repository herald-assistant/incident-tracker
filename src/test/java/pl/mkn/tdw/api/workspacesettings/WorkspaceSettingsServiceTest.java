package pl.mkn.tdw.api.workspacesettings;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.api.uiconfig.UiConfigProperties;
import pl.mkn.tdw.integrations.dynatrace.DynatraceProperties;
import pl.mkn.tdw.integrations.elasticsearch.ElasticProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsFile;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsStore;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsDynatraceUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsElasticsearchUpdate;
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
        assertThat(response.values().elasticsearch().baseUrl().value()).isEqualTo("https://elastic.app");
        assertThat(response.values().elasticsearch().kibanaSpaceId().value()).isEqualTo("default");
        assertThat(response.values().elasticsearch().indexPattern().value()).isEqualTo("logs-*");
        assertThat(response.values().elasticsearch().authorizationHeader().value()).isEqualTo("Bearer app-token");
        assertThat(response.values().elasticsearch().authorizationHeader().secret()).isTrue();
        assertThat(response.values().dynatrace().baseUrl().value()).isEqualTo("https://dynatrace.app");
        assertThat(response.values().dynatrace().apiToken().value()).isEqualTo("dt0c01.app-token");
        assertThat(response.values().dynatrace().apiToken().secret()).isTrue();
        assertThat(fixture.gitLabProperties.getBaseUrl()).isEqualTo("https://gitlab.app");
        assertThat(fixture.elasticProperties.getBaseUrl()).isEqualTo("https://elastic.app");
        assertThat(fixture.dynatraceProperties.getBaseUrl()).isEqualTo("https://dynatrace.app");
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
                ),
                new WorkspaceSettingsElasticsearchUpdate(
                        "https://elastic.workspace",
                        "default",
                        "logs-platform-*",
                        "Bearer workspace-token"
                ),
                new WorkspaceSettingsDynatraceUpdate(
                        "https://dynatrace.app",
                        "dt0c01.workspace-token"
                )
        ));

        assertThat(fixture.store.saved.gitLab().baseUrl()).isNull();
        assertThat(fixture.store.saved.gitLab().group()).isEqualTo("workspace/group");
        assertThat(fixture.store.saved.gitLab().token()).isEqualTo("workspace-token");
        assertThat(fixture.store.saved.elasticsearch().baseUrl()).isEqualTo("https://elastic.workspace");
        assertThat(fixture.store.saved.elasticsearch().kibanaSpaceId()).isNull();
        assertThat(fixture.store.saved.elasticsearch().indexPattern()).isEqualTo("logs-platform-*");
        assertThat(fixture.store.saved.elasticsearch().authorizationHeader()).isEqualTo("Bearer workspace-token");
        assertThat(fixture.store.saved.dynatrace().baseUrl()).isNull();
        assertThat(fixture.store.saved.dynatrace().apiToken()).isEqualTo("dt0c01.workspace-token");
        assertThat(response.values().gitLab().baseUrl().source()).isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().gitLab().group().source()).isEqualTo(WorkspaceSettingsSource.WORKSPACE_SETTINGS);
        assertThat(response.values().elasticsearch().baseUrl().source()).isEqualTo(WorkspaceSettingsSource.WORKSPACE_SETTINGS);
        assertThat(response.values().elasticsearch().kibanaSpaceId().source())
                .isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().dynatrace().baseUrl().source())
                .isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().dynatrace().apiToken().source())
                .isEqualTo(WorkspaceSettingsSource.WORKSPACE_SETTINGS);
        assertThat(fixture.uiConfigProperties.getTitle()).isEqualTo("Workspace override");
        assertThat(fixture.gitLabProperties.getBaseUrl()).isEqualTo("https://gitlab.app");
        assertThat(fixture.gitLabProperties.getGroup()).isEqualTo("workspace/group");
        assertThat(fixture.gitLabProperties.getToken()).isEqualTo("workspace-token");
        assertThat(fixture.elasticProperties.getBaseUrl()).isEqualTo("https://elastic.workspace");
        assertThat(fixture.elasticProperties.getKibanaSpaceId()).isEqualTo("default");
        assertThat(fixture.elasticProperties.getIndexPattern()).isEqualTo("logs-platform-*");
        assertThat(fixture.elasticProperties.getAuthorizationHeader()).isEqualTo("Bearer workspace-token");
        assertThat(fixture.dynatraceProperties.getBaseUrl()).isEqualTo("https://dynatrace.app");
        assertThat(fixture.dynatraceProperties.getApiToken()).isEqualTo("dt0c01.workspace-token");
    }

    @Test
    void shouldClearOverrideWhenSavedValueMatchesApplicationProperties() {
        var fixture = fixture();
        fixture.service.initialize();
        fixture.service.saveSettings(new WorkspaceSettingsUpdateRequest(
                new WorkspaceSettingsAppUiUpdate("Workspace override"),
                new WorkspaceSettingsGitLabUpdate("https://gitlab.workspace", "workspace/group", "workspace-token"),
                new WorkspaceSettingsElasticsearchUpdate(
                        "https://elastic.workspace",
                        "observability",
                        "logs-platform-*",
                        "Bearer workspace-token"
                ),
                new WorkspaceSettingsDynatraceUpdate(
                        "https://dynatrace.workspace",
                        "dt0c01.workspace-token"
                )
        ));

        var response = fixture.service.saveSettings(new WorkspaceSettingsUpdateRequest(
                new WorkspaceSettingsAppUiUpdate("App workspace"),
                new WorkspaceSettingsGitLabUpdate("https://gitlab.app", "app/group", "app-token"),
                new WorkspaceSettingsElasticsearchUpdate(
                        "https://elastic.app",
                        "default",
                        "logs-*",
                        "Bearer app-token"
                ),
                new WorkspaceSettingsDynatraceUpdate(
                        "https://dynatrace.app",
                        "dt0c01.app-token"
                )
        ));

        assertThat(fixture.store.saved.appUi().title()).isNull();
        assertThat(fixture.store.saved.gitLab().baseUrl()).isNull();
        assertThat(fixture.store.saved.gitLab().group()).isNull();
        assertThat(fixture.store.saved.gitLab().token()).isNull();
        assertThat(fixture.store.saved.elasticsearch().baseUrl()).isNull();
        assertThat(fixture.store.saved.elasticsearch().kibanaSpaceId()).isNull();
        assertThat(fixture.store.saved.elasticsearch().indexPattern()).isNull();
        assertThat(fixture.store.saved.elasticsearch().authorizationHeader()).isNull();
        assertThat(fixture.store.saved.dynatrace().baseUrl()).isNull();
        assertThat(fixture.store.saved.dynatrace().apiToken()).isNull();
        assertThat(response.values().appUi().title().source()).isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(response.values().elasticsearch().indexPattern().source())
                .isEqualTo(WorkspaceSettingsSource.APPLICATION_PROPERTIES);
        assertThat(fixture.uiConfigProperties.getTitle()).isEqualTo("App workspace");
        assertThat(fixture.gitLabProperties.getGroup()).isEqualTo("app/group");
        assertThat(fixture.elasticProperties.getIndexPattern()).isEqualTo("logs-*");
        assertThat(fixture.dynatraceProperties.getApiToken()).isEqualTo("dt0c01.app-token");
    }

    private Fixture fixture() {
        var uiConfigProperties = new UiConfigProperties();
        uiConfigProperties.setTitle("App workspace");
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setBaseUrl("https://gitlab.app");
        gitLabProperties.setGroup("app/group");
        gitLabProperties.setToken("app-token");
        var elasticProperties = new ElasticProperties();
        elasticProperties.setBaseUrl("https://elastic.app");
        elasticProperties.setKibanaSpaceId("default");
        elasticProperties.setIndexPattern("logs-*");
        elasticProperties.setAuthorizationHeader("Bearer app-token");
        var dynatraceProperties = new DynatraceProperties();
        dynatraceProperties.setBaseUrl("https://dynatrace.app");
        dynatraceProperties.setApiToken("dt0c01.app-token");
        var store = new InMemoryLocalWorkspaceSettingsStore();
        return new Fixture(
                uiConfigProperties,
                gitLabProperties,
                elasticProperties,
                dynatraceProperties,
                store,
                new WorkspaceSettingsService(
                        store,
                        uiConfigProperties,
                        gitLabProperties,
                        elasticProperties,
                        dynatraceProperties
                )
        );
    }

    private record Fixture(
            UiConfigProperties uiConfigProperties,
            GitLabProperties gitLabProperties,
            ElasticProperties elasticProperties,
            DynatraceProperties dynatraceProperties,
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
