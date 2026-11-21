package pl.mkn.tdw.features.systemassistant;

import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.features.systemassistant.config.*;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import static org.mockito.Mockito.*;

final class SystemAssistantTestSupport {
    static pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationHistory history(java.nio.file.Path directory) {
        var properties = new pl.mkn.tdw.localworkspace.LocalWorkspaceProperties(); properties.setDirectory(directory.toString());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        return new pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationHistory(
                new pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore(properties,
                        new pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths(properties),
                        new pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore(mapper)), mapper, properties);
    }

    static SystemAssistantConfigurationStore store() {
        var store = mock(SystemAssistantConfigurationStore.class);
        when(store.read()).thenReturn(SystemAssistantConfiguration.empty()); return store;
    }
    static SystemAssistantConfigurationService configuration(OpenApiImportPort importer) {
        return new SystemAssistantConfigurationService(importer, store(), new OpenApiToolCatalog());
    }
}
