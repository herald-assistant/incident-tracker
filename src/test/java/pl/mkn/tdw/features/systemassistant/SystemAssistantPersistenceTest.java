package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.features.systemassistant.config.*;
import pl.mkn.tdw.features.systemassistant.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.*;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemAssistantPersistenceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private SystemAssistantConfiguration configuration(String text) {
        return SystemAssistantTestSupport.config(List.of(), List.of(new SystemAssistantConfiguration.Skill("demo", "Demo", text)));
    }
    @Test
    void rejectsCollidingGeneratedNamesAndKeepsAcceptedCallbacksUnchanged() throws Exception {
        var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"));
        var service = SystemAssistantTestSupport.configuration(new OpenApiImporter(mapper));
        var original = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm_a", "Demo", yaml)), List.of());
        service.save(original);
        var seen = new java.util.concurrent.atomic.AtomicReference<String>();
        var factory = new pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory((execution, args) -> {
            seen.set(execution.operation().path());
            return new pl.mkn.tdw.integrations.openapi.contract.OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "Demo");
        }, mapper);
        var accepted = factory.create(service.snapshot().forProfile("test_profile").executions()).get(0);
        var colliding = yaml.replace("operationId: ", "operationId: a_");
        assertThatThrownBy(() -> service.save(SystemAssistantTestSupport.config(List.of(original.apis().get(0),
                new SystemAssistantConfiguration.ApiDefinition("crm", "Collision", colliding)), List.of())))
                .isInstanceOf(SystemAssistantException.class).hasMessageContaining("powtórzone nazwy");
        assertThat(service.current()).isEqualTo(original);
        service.save(SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm_a", "Edited", yaml.replace("/tickets/{id}", "/changed/{id}"))), List.of()));
        accepted.call("{\"path\":{\"id\":\"CRM-101\"}}");
        assertThat(seen.get()).isEqualTo("/tickets/{id}");
        factory.create(service.snapshot().forProfile("test_profile").executions()).get(0).call("{\"path\":{\"id\":\"CRM-101\"}}");
        assertThat(seen.get()).isEqualTo("/changed/{id}");
    }

    @Test
    void keepsOnlyCurrentConfigurationAndBlocksCorruptDataUntilSaved() throws Exception {
        var properties = new LocalWorkspaceProperties(); properties.setDirectory(directory.toString());
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var store = new SystemAssistantConfigurationStore(new LocalWorkspacePaths(properties),
                    new LocalWorkspaceJsonFileStore(mapper), validation.getValidator());
            var service = new SystemAssistantConfigurationService(new OpenApiImporter(mapper), store, new OpenApiToolCatalog());
            service.load(); service.save(configuration("FIRST")); service.save(configuration("CURRENT"));
            var restored = new SystemAssistantConfigurationService(new OpenApiImporter(mapper), store, new OpenApiToolCatalog());
            restored.load(); assertThat(restored.current().skills().get(0).content()).isEqualTo("CURRENT");
            var path = directory.resolve("system-assistant/configuration.json");
            assertThat(Files.readString(path)).doesNotContain("FIRST", "revision", "digest");
            try (var files = Files.list(path.getParent())) { assertThat(files.toList()).containsExactly(path); }
            Files.writeString(path, "{broken"); restored.load();
            assertThatThrownBy(restored::snapshot).isInstanceOf(SystemAssistantException.class);
            restored.save(configuration("REPAIRED"));
            assertThat(restored.current().skills().get(0).content()).isEqualTo("REPAIRED");
        }
    }
    @Test
    void failedPublicationOrSaveNeverActivatesNewConfiguration() {
        var store = SystemAssistantTestSupport.store();
        var catalog = new OpenApiToolCatalog();
        var service = new SystemAssistantConfigurationService((id, yaml) -> List.of(), store, catalog);
        service.save(configuration("OLD"));
        doThrow(new IllegalStateException("synthetic disk failure")).when(store).write(configuration("NEW"));
        assertThatThrownBy(() -> service.save(configuration("NEW"))).isInstanceOf(IllegalStateException.class);
        assertThat(service.current().skills().get(0).content()).isEqualTo("OLD");
        assertThat(catalog.current()).containsOnlyKeys("test_profile");
        assertThat(catalog.current().get("test_profile")).isEmpty();
    }
}
