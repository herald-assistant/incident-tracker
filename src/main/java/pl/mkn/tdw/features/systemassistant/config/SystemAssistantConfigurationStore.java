package pl.mkn.tdw.features.systemassistant.config;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.nio.file.Files;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantConfigurationStore {
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore files;
    private final Validator validator;

    public record File(int version, SystemAssistantConfiguration configuration) {
    }

    private java.nio.file.Path path() {
        return paths.root().resolve("system-assistant/configuration.json");
    }

    public SystemAssistantConfiguration read() {
        if (!Files.exists(path())) return SystemAssistantConfiguration.empty();
        var file = files.read(path(), File.class).orElseThrow(() -> invalid());
        if (file.version() != 1 || file.configuration() == null || !validator.validate(file.configuration()).isEmpty())
            throw invalid();
        return file.configuration();
    }

    public void write(SystemAssistantConfiguration value) {
        try {
            files.writeAtomic(path(), new File(1, value));
        } catch (RuntimeException exception) {
            throw new SystemAssistantException("CONFIGURATION_SAVE_FAILED",
                    UserFacingErrorType.SERVICE_UNAVAILABLE, "Nie udało się zapisać konfiguracji. Poprzedni zestaw pozostaje aktywny.");
        }
    }

    private SystemAssistantException invalid() {
        return new SystemAssistantException("CONFIGURATION_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Bieżąca konfiguracja jest uszkodzona lub ma nieobsługiwany format. Popraw ją i zapisz; nie odtworzono dawnej wersji.");
    }
}
