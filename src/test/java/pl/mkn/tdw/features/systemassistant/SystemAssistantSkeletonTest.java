package pl.mkn.tdw.features.systemassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pl.mkn.tdw.api.ApiExceptionHandler;
import pl.mkn.tdw.features.systemassistant.api.SystemAssistantConfigurationController;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemAssistantSkeletonTest {
    @Test
    void toolsIdentifyTheirContractEvenWhenNamesHaveOverlappingPrefixes() throws Exception {
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        OpenApiImportPort importer = (id, yaml) -> List.of(new pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation(
                id.equals("crm") ? "oa_crm_a_read" : "oa_crm_a_write", "Demo", "GET",
                java.net.URI.create("http://127.0.0.1:8099"), "/" + id, List.of(), schema, schema, "UNKNOWN", java.util.Map.of()));
        var service = SystemAssistantTestSupport.configuration(importer);
        service.save(SystemAssistantTestSupport.config(List.of(
                new pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration.ApiDefinition("crm", "CRM", "yaml"),
                new pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration.ApiDefinition("crm_a", "Other", "yaml")), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantConfigurationController(service)).build();
        mvc.perform(get("/api/system-assistant/configuration/profiles"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json(
                        "[{\"id\":\"test_profile\",\"name\":\"Test CRM\",\"urlPattern\":\"*\"}]", org.springframework.test.json.JsonCompareMode.STRICT));
        mvc.perform(get("/api/system-assistant/configuration/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiId").value("crm"))
                .andExpect(jsonPath("$[0].name").value("oa_crm_a_read"))
                .andExpect(jsonPath("$[1].apiId").value("crm_a"))
                .andExpect(jsonPath("$[1].name").value("oa_crm_a_write"));
    }

    @Test
    void validatesConfigurationAndAcceptsEmptySet() throws Exception {
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantConfigurationController(
                    SystemAssistantTestSupport.configuration((id, yaml) -> List.of()))).setControllerAdvice(new ApiExceptionHandler())
                    .setValidator(validator).build();
            mvc.perform(put("/api/system-assistant/configuration").contentType("application/json")
                    .content("{\"apis\":null,\"skills\":[]}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mvc.perform(put("/api/system-assistant/configuration").contentType("application/json")
                    .content("{\"apis\":[],\"skills\":[],\"profiles\":[],\"authProfiles\":[]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.apis").isEmpty());
        }
    }

    @Test
    void activationDoesNotDependOnServerBindAddress() {
        var runner = new ApplicationContextRunner()
                .withBean(pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationStore.class, SystemAssistantTestSupport::store)
                .withBean(pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog.class).withBean(OpenApiImportPort.class, () -> (id, yaml) -> List.of()).withUserConfiguration(
                SystemAssistantConfigurationService.class,
                SystemAssistantConfigurationController.class);
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(SystemAssistantConfigurationController.class));
        runner.withPropertyValues("system-assistant.enabled=true")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(SystemAssistantConfigurationController.class));
        for (var address : List.of("0.0.0.0", "127.0.0.1", "::", "192.0.2.10")) {
            runner.withPropertyValues("system-assistant.enabled=true", "server.address=" + address)
                    .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(SystemAssistantConfigurationController.class));
        }
    }
}
