package pl.mkn.tdw.features.systemassistant;

import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.features.systemassistant.config.*;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import static org.mockito.Mockito.*;

final class SystemAssistantTestSupport {
    static SystemAssistantConfiguration config(java.util.List<SystemAssistantConfiguration.ApiDefinition> apis,
            java.util.List<SystemAssistantConfiguration.Skill> skills) {
        return new SystemAssistantConfiguration(apis, skills, java.util.List.of(new SystemAssistantConfiguration.Profile("test_profile", "Test CRM", "*", (apis.stream().map(SystemAssistantConfiguration.ApiDefinition::id).toList()).stream().map(apiId -> new SystemAssistantConfiguration.ApiBinding((String) apiId, null)).toList(), skills.stream().map(SystemAssistantConfiguration.Skill::id).toList())), java.util.List.of());
    }
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
        when(store.read()).thenReturn(config(java.util.List.of(), java.util.List.of())); return store;
    }
    static SystemAssistantConfigurationService configuration(OpenApiImportPort importer) {
        var service = new SystemAssistantConfigurationService(importer, store(), new OpenApiToolCatalog());
        service.load();
        return service;
    }
}
