package pl.mkn.tdw.api.workspacesettings;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.api.uiconfig.UiConfigProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceAppUiSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceGitLabSettings;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsFile;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsStore;

import java.util.Objects;

import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsAppUiUpdate;
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
    private final GitLabProperties gitLabProperties;

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
        var gitLab = request != null && request.gitLab() != null
                ? request.gitLab()
                : new WorkspaceSettingsGitLabUpdate(null, null, null);

        var settings = new LocalWorkspaceSettingsFile(
                LocalWorkspaceSettingsFile.SCHEMA,
                LocalWorkspaceSettingsFile.VERSION,
                new LocalWorkspaceAppUiSettings(overrideValue(appUi.title(), application().appUi().title())),
                new LocalWorkspaceGitLabSettings(
                        overrideValue(gitLab.baseUrl(), application().gitLab().baseUrl()),
                        overrideValue(gitLab.group(), application().gitLab().group()),
                        overrideValue(gitLab.token(), application().gitLab().token())
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
                        new WorkspaceSettingsGitLabResponse(
                                field("analysis.gitlab.base-url", app.gitLab().baseUrl(), file.gitLab().baseUrl(), false),
                                field("analysis.gitlab.group", app.gitLab().group(), file.gitLab().group(), false),
                                field("analysis.gitlab.token", app.gitLab().token(), file.gitLab().token(), true)
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
        gitLabProperties.setBaseUrl(effectiveValue(file.gitLab().baseUrl(), app.gitLab().baseUrl()));
        gitLabProperties.setGroup(effectiveValue(file.gitLab().group(), app.gitLab().group()));
        gitLabProperties.setToken(effectiveValue(file.gitLab().token(), app.gitLab().token()));
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
                new GitLabSettings(
                        normalize(gitLabProperties.getBaseUrl()),
                        normalize(gitLabProperties.getGroup()),
                        normalize(gitLabProperties.getToken())
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record WorkspaceSettingsValues(
            AppUiSettings appUi,
            GitLabSettings gitLab
    ) {
    }

    private record AppUiSettings(
            String title
    ) {
    }

    private record GitLabSettings(
            String baseUrl,
            String group,
            String token
    ) {
    }
}
