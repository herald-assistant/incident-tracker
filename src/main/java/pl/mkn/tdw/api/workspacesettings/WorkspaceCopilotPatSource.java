package pl.mkn.tdw.api.workspacesettings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotPatSource;
import pl.mkn.tdw.localworkspace.settings.LocalWorkspaceSettingsStore;

@Component
@RequiredArgsConstructor
public class WorkspaceCopilotPatSource implements CopilotPatSource {

    private final LocalWorkspaceSettingsStore settingsStore;

    @Override
    public String currentPat() {
        var settings = settingsStore.read();
        return settings != null && settings.copilot() != null
                ? settings.copilot().localGithubToken()
                : null;
    }
}
