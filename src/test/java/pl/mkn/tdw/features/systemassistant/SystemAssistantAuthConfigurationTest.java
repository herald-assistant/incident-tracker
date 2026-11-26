package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SystemAssistantAuthConfigurationTest {
    @org.junit.jupiter.api.io.TempDir Path directory;

    @Test void persistsPlainCredentialsInCurrentConfigurationAndRejectsPreviousShape() throws Exception {
        var mapper = new ObjectMapper();
        var properties = new pl.mkn.tdw.localworkspace.LocalWorkspaceProperties();
        properties.setDirectory(directory.toString());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var store = new pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationStore(
                    new pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths(properties),
                    new pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore(mapper), factory.getValidator());
            var auth = new HttpAuthProfile("crm_auth", "CRM test", "BASIC", "operator", "public-test-password",
                    null, null, null, null, null, null, null, null);
            var configuration = new SystemAssistantConfiguration(
                    List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", "test YAML")), List.of(),
                    List.of(new SystemAssistantConfiguration.Profile("operator", "Operator", "*",
                            List.of(new SystemAssistantConfiguration.ApiBinding("crm", "crm_auth")), List.of())), List.of(auth));
            store.write(configuration);
            assertThat(store.read()).isEqualTo(configuration);
            var path = directory.resolve("system-assistant/configuration.json");
            assertThat(Files.readString(path)).contains("public-test-password", "authProfiles", "apiBindings").doesNotContain("apiIds");
            var oldShape = mapper.readTree(Files.readString(path));
            var oldProfile = (com.fasterxml.jackson.databind.node.ObjectNode) oldShape.path("configuration").path("profiles").get(0);
            oldProfile.remove("apiBindings");
            oldProfile.putArray("apiIds").add("crm");
            Files.writeString(path, mapper.writeValueAsString(oldShape));
            assertThatThrownBy(store::read).hasMessageContaining("nieobsługiwany format");
            Files.writeString(path, "{\"version\":1,\"configuration\":{\"apis\":[],\"skills\":[],\"profiles\":[]}}");
            assertThatThrownBy(store::read).hasMessageContaining("nieobsługiwany format");
        }
    }

    @Test void capturesDifferentAuthenticationForTheSameApiAndRejectsDanglingBindings() throws Exception {
        var service = SystemAssistantTestSupport.configuration(new OpenApiImporter(new ObjectMapper()));
        var api = new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml")));
        var first = new HttpAuthProfile("first_auth", "First", "BASIC", "first", "test-password", null, null, null, null, null, null, null, null);
        var second = new HttpAuthProfile("second_auth", "Second", "BASIC", "second", "other-password", null, null, null, null, null, null, null, null);
        var profiles = List.of(
                new SystemAssistantConfiguration.Profile("first", "First", "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm", "first_auth")), List.of()),
                new SystemAssistantConfiguration.Profile("second", "Second", "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm", "second_auth")), List.of()),
                new SystemAssistantConfiguration.Profile("anonymous", "Anonymous", "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm", null)), List.of()));
        var current = new SystemAssistantConfiguration(List.of(api), List.of(), profiles, List.of(first, second));
        service.save(current);
        var captured = service.snapshot().forProfile("first").executions();
        assertThat(captured).hasSize(3).allSatisfy(binding -> {
            assertThat(binding.authentication()).isEqualTo(first);
            assertThat(binding.operation().inputSchema().toString()).doesNotContain("test-password", "Authorization", "authProfileId");
        });
        assertThat(service.snapshot().forProfile("second").executions()).allSatisfy(binding -> assertThat(binding.authentication()).isEqualTo(second));
        assertThat(service.snapshot().forProfile("anonymous").executions()).allSatisfy(binding -> assertThat(binding.authentication()).isNull());
        assertThatThrownBy(() -> service.snapshot().forProfile("missing")).hasMessageContaining("Wybierz istniejący profil");
        assertThatThrownBy(() -> service.save(new SystemAssistantConfiguration(List.of(api), List.of(), profiles, List.of())))
                .hasMessageContaining("nieistniejące uwierzytelnianie");
        assertThat(service.current()).isEqualTo(current);
        var replacement = List.of(new SystemAssistantConfiguration.Profile("first", "First", "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm", "second_auth")), List.of()));
        service.save(new SystemAssistantConfiguration(List.of(api), List.of(), replacement, List.of(first, second)));
        assertThat(captured).allSatisfy(binding -> assertThat(binding.authentication()).isEqualTo(first));
        assertThat(service.snapshot().forProfile("first").executions()).allSatisfy(binding -> assertThat(binding.authentication()).isEqualTo(second));
        assertThatThrownBy(() -> service.save(new SystemAssistantConfiguration(List.of(api), List.of(), List.of(
                new SystemAssistantConfiguration.Profile("bad", "Bad", "*", List.of(new SystemAssistantConfiguration.ApiBinding("crm", null), new SystemAssistantConfiguration.ApiBinding("crm", "first_auth")), List.of())), List.of(first))))
                .hasMessageContaining("powtarzać");
    }
}
