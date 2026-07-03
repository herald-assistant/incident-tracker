package pl.mkn.tdw.api.workspacesettings;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.api.uiconfig.UiConfigProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.integrations.dynatrace.DynatraceProperties;
import pl.mkn.tdw.integrations.elasticsearch.ElasticProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceAppUiSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceCopilotSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceDynatraceSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceElasticsearchSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceGitLabSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsFile;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsStore;

import java.util.Objects;

import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsCopilotResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsCopilotUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsDynatraceResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsDynatraceUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsElasticsearchResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsElasticsearchUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsFieldResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsGitLabResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsGitLabUpdate;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsSource;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsUpdateRequest;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsValuesResponse;

@Service
@RequiredArgsConstructor
public class WorkspaceSettingsService {

    private final LocalWorkspaceSettingsStore settingsStore;
    private final UiConfigProperties uiConfigProperties;
    private final CopilotSdkProperties copilotSdkProperties;
    private final GitLabProperties gitLabProperties;
    private final ElasticProperties elasticProperties;
    private final DynatraceProperties dynatraceProperties;

    private WorkspaceSettingsValues applicationValues;

    @PostConstruct
    void initialize() {
        applicationValues = snapshotApplicationValues();
        applySettings(settingsStore.read());
    }

    public synchronized WorkspaceSettingsResponse currentSettings() {
        var settings = settingsStore.read();
        return toResponse(settings);
    }

    public synchronized WorkspaceSettingsResponse saveSettings(WorkspaceSettingsUpdateRequest request) {
        if (!settingsStore.enabled()) {
            return toResponse(settingsStore.read());
        }

        var appUi = request != null && request.appUi() != null
                ? request.appUi()
                : new WorkspaceSettingsAppUiUpdate(null);
        var copilot = request != null && request.copilot() != null
                ? request.copilot()
                : new WorkspaceSettingsCopilotUpdate(null);
        var gitLab = request != null && request.gitLab() != null
                ? request.gitLab()
                : new WorkspaceSettingsGitLabUpdate(null, null, null);
        var elasticsearch = request != null && request.elasticsearch() != null
                ? request.elasticsearch()
                : new WorkspaceSettingsElasticsearchUpdate(null, null, null, null);
        var dynatrace = request != null && request.dynatrace() != null
                ? request.dynatrace()
                : new WorkspaceSettingsDynatraceUpdate(null, null);

        var settings = new LocalWorkspaceSettingsFile(
                LocalWorkspaceSettingsFile.SCHEMA,
                LocalWorkspaceSettingsFile.VERSION,
                new LocalWorkspaceAppUiSettings(overrideValue(appUi.title(), application().appUi().title())),
                new LocalWorkspaceCopilotSettings(
                        overrideValue(
                                copilot.localGithubToken(),
                                application().copilot().localGithubToken()
                        )
                ),
                new LocalWorkspaceGitLabSettings(
                        overrideValue(gitLab.baseUrl(), application().gitLab().baseUrl()),
                        overrideValue(gitLab.group(), application().gitLab().group()),
                        overrideValue(gitLab.token(), application().gitLab().token())
                ),
                new LocalWorkspaceElasticsearchSettings(
                        overrideValue(elasticsearch.baseUrl(), application().elasticsearch().baseUrl()),
                        overrideValue(elasticsearch.kibanaSpaceId(), application().elasticsearch().kibanaSpaceId()),
                        overrideValue(elasticsearch.indexPattern(), application().elasticsearch().indexPattern()),
                        overrideValue(
                                elasticsearch.authorizationHeader(),
                                application().elasticsearch().authorizationHeader()
                        )
                ),
                new LocalWorkspaceDynatraceSettings(
                        overrideValue(dynatrace.baseUrl(), application().dynatrace().baseUrl()),
                        overrideValue(dynatrace.apiToken(), application().dynatrace().apiToken())
                )
        );

        var saved = settingsStore.save(settings);
        applySettings(saved);
        return toResponse(saved);
    }

    private WorkspaceSettingsResponse toResponse(LocalWorkspaceSettingsFile settings) {
        var app = application();
        var file = settings == null ? LocalWorkspaceSettingsFile.empty() : settings;

        return new WorkspaceSettingsResponse(
                settingsStore.enabled(),
                settingsStore.settingsPath().toString(),
                new WorkspaceSettingsValuesResponse(
                        new WorkspaceSettingsAppUiResponse(field(
                                "app.ui.title",
                                app.appUi().title(),
                                file.appUi().title(),
                                false
                        )),
                        new WorkspaceSettingsCopilotResponse(field(
                                "analysis.ai.copilot.auth.local.github-token",
                                app.copilot().localGithubToken(),
                                file.copilot().localGithubToken(),
                                true
                        )),
                        new WorkspaceSettingsGitLabResponse(
                                field("analysis.gitlab.base-url", app.gitLab().baseUrl(), file.gitLab().baseUrl(), false),
                                field("analysis.gitlab.group", app.gitLab().group(), file.gitLab().group(), false),
                                field("analysis.gitlab.token", app.gitLab().token(), file.gitLab().token(), true)
                        ),
                        new WorkspaceSettingsElasticsearchResponse(
                                field(
                                        "analysis.elasticsearch.base-url",
                                        app.elasticsearch().baseUrl(),
                                        file.elasticsearch().baseUrl(),
                                        false
                                ),
                                field(
                                        "analysis.elasticsearch.kibana-space-id",
                                        app.elasticsearch().kibanaSpaceId(),
                                        file.elasticsearch().kibanaSpaceId(),
                                        false
                                ),
                                field(
                                        "analysis.elasticsearch.index-pattern",
                                        app.elasticsearch().indexPattern(),
                                        file.elasticsearch().indexPattern(),
                                        false
                                ),
                                field(
                                        "analysis.elasticsearch.authorization-header",
                                        app.elasticsearch().authorizationHeader(),
                                        file.elasticsearch().authorizationHeader(),
                                        true
                                )
                        ),
                        new WorkspaceSettingsDynatraceResponse(
                                field(
                                        "analysis.dynatrace.base-url",
                                        app.dynatrace().baseUrl(),
                                        file.dynatrace().baseUrl(),
                                        false
                                ),
                                field(
                                        "analysis.dynatrace.api-token",
                                        app.dynatrace().apiToken(),
                                        file.dynatrace().apiToken(),
                                        true
                                )
                        )
                )
        );
    }

    private WorkspaceSettingsFieldResponse field(
            String propertyKey,
            String applicationValue,
            String workspaceValue,
            boolean secret
    ) {
        var normalizedApplicationValue = valueOrEmpty(applicationValue);
        var normalizedWorkspaceValue = normalize(workspaceValue);
        var hasWorkspaceValue = StringUtils.hasText(normalizedWorkspaceValue);
        return new WorkspaceSettingsFieldResponse(
                propertyKey,
                hasWorkspaceValue ? normalizedWorkspaceValue : normalizedApplicationValue,
                normalizedApplicationValue,
                hasWorkspaceValue ? normalizedWorkspaceValue : null,
                hasWorkspaceValue
                        ? WorkspaceSettingsSource.WORKSPACE_SETTINGS
                        : WorkspaceSettingsSource.APPLICATION_PROPERTIES,
                secret
        );
    }

    private void applySettings(LocalWorkspaceSettingsFile settings) {
        var app = application();
        var file = settings == null ? LocalWorkspaceSettingsFile.empty() : settings;

        uiConfigProperties.setTitle(effectiveValue(file.appUi().title(), app.appUi().title()));
        copilotLocalProperties().setGithubToken(effectiveValue(
                file.copilot().localGithubToken(),
                app.copilot().localGithubToken()
        ));
        gitLabProperties.setBaseUrl(effectiveValue(file.gitLab().baseUrl(), app.gitLab().baseUrl()));
        gitLabProperties.setGroup(effectiveValue(file.gitLab().group(), app.gitLab().group()));
        gitLabProperties.setToken(effectiveValue(file.gitLab().token(), app.gitLab().token()));
        elasticProperties.setBaseUrl(effectiveValue(file.elasticsearch().baseUrl(), app.elasticsearch().baseUrl()));
        elasticProperties.setKibanaSpaceId(effectiveValue(
                file.elasticsearch().kibanaSpaceId(),
                app.elasticsearch().kibanaSpaceId()
        ));
        elasticProperties.setIndexPattern(effectiveValue(
                file.elasticsearch().indexPattern(),
                app.elasticsearch().indexPattern()
        ));
        elasticProperties.setAuthorizationHeader(effectiveValue(
                file.elasticsearch().authorizationHeader(),
                app.elasticsearch().authorizationHeader()
        ));
        dynatraceProperties.setBaseUrl(effectiveValue(file.dynatrace().baseUrl(), app.dynatrace().baseUrl()));
        dynatraceProperties.setApiToken(effectiveValue(file.dynatrace().apiToken(), app.dynatrace().apiToken()));
    }

    private WorkspaceSettingsValues application() {
        if (applicationValues == null) {
            applicationValues = snapshotApplicationValues();
        }
        return applicationValues;
    }

    private WorkspaceSettingsValues snapshotApplicationValues() {
        return new WorkspaceSettingsValues(
                new AppUiSettings(normalize(uiConfigProperties.getTitle())),
                new CopilotSettings(normalize(copilotLocalGithubToken())),
                new GitLabSettings(
                        normalize(gitLabProperties.getBaseUrl()),
                        normalize(gitLabProperties.getGroup()),
                        normalize(gitLabProperties.getToken())
                ),
                new ElasticsearchSettings(
                        normalize(elasticProperties.getBaseUrl()),
                        normalize(elasticProperties.getKibanaSpaceId()),
                        normalize(elasticProperties.getIndexPattern()),
                        normalize(elasticProperties.getAuthorizationHeader())
                ),
                new DynatraceSettings(
                        normalize(dynatraceProperties.getBaseUrl()),
                        normalize(dynatraceProperties.getApiToken())
                )
        );
    }

    private String overrideValue(String value, String applicationValue) {
        var normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return Objects.equals(normalized, normalize(applicationValue)) ? null : normalized;
    }

    private String effectiveValue(String workspaceValue, String applicationValue) {
        var normalizedWorkspaceValue = normalize(workspaceValue);
        return StringUtils.hasText(normalizedWorkspaceValue)
                ? normalizedWorkspaceValue
                : normalize(applicationValue);
    }

    private String valueOrEmpty(String value) {
        var normalized = normalize(value);
        return normalized == null ? "" : normalized;
    }

    private String copilotLocalGithubToken() {
        if (copilotSdkProperties.getAuth() == null || copilotSdkProperties.getAuth().getLocal() == null) {
            return null;
        }
        return copilotSdkProperties.getAuth().getLocal().getGithubToken();
    }

    private CopilotSdkProperties.Local copilotLocalProperties() {
        if (copilotSdkProperties.getAuth() == null) {
            copilotSdkProperties.setAuth(new CopilotSdkProperties.Auth());
        }
        if (copilotSdkProperties.getAuth().getLocal() == null) {
            copilotSdkProperties.getAuth().setLocal(new CopilotSdkProperties.Local());
        }
        return copilotSdkProperties.getAuth().getLocal();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record WorkspaceSettingsValues(
            AppUiSettings appUi,
            CopilotSettings copilot,
            GitLabSettings gitLab,
            ElasticsearchSettings elasticsearch,
            DynatraceSettings dynatrace
    ) {
    }

    private record AppUiSettings(
            String title
    ) {
    }

    private record CopilotSettings(
            String localGithubToken
    ) {
    }

    private record GitLabSettings(
            String baseUrl,
            String group,
            String token
    ) {
    }

    private record ElasticsearchSettings(
            String baseUrl,
            String kibanaSpaceId,
            String indexPattern,
            String authorizationHeader
    ) {
    }

    private record DynatraceSettings(
            String baseUrl,
            String apiToken
    ) {
    }
}
