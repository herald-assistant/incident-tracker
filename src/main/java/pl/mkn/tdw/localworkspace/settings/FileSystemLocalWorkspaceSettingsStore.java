package pl.mkn.tdw.localworkspace.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class FileSystemLocalWorkspaceSettingsStore implements LocalWorkspaceSettingsStore {

    private final LocalWorkspaceProperties properties;
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    @Override
    public synchronized LocalWorkspaceSettingsFile read() {
        if (!properties.isEnabled()) {
            return LocalWorkspaceSettingsFile.empty();
        }

        return jsonFileStore.read(paths.settingsFile(), LocalWorkspaceSettingsFile.class)
                .orElseGet(LocalWorkspaceSettingsFile::empty);
    }

    @Override
    public synchronized LocalWorkspaceSettingsFile save(LocalWorkspaceSettingsFile settings) {
        var normalized = settings == null ? LocalWorkspaceSettingsFile.empty() : settings;
        if (!properties.isEnabled()) {
            return normalized;
        }

        jsonFileStore.writeAtomic(paths.settingsFile(), normalized);
        return normalized;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public Path settingsPath() {
        return paths.settingsFile();
    }
}
