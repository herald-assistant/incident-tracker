package pl.mkn.tdw.localworkspace.settings;

import java.nio.file.Path;

public interface LocalWorkspaceSettingsStore {

    LocalWorkspaceSettingsFile read();

    LocalWorkspaceSettingsFile save(LocalWorkspaceSettingsFile settings);

    boolean enabled();

    Path settingsPath();
}
