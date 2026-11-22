package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pl.mkn.tdw.api.ApiExceptionHandler;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.features.systemassistant.api.*;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.openapi.service.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemAssistantOpenApiSelectionTest {
    @Test void preparesWithoutPublishingAndPersistsOnlyTheChosenYamlAfterExplicitSave() throws Exception {
        var mapper = new ObjectMapper();
        var importer = new OpenApiImporter(mapper);
        var store = SystemAssistantTestSupport.store();
        var configurations = new SystemAssistantConfigurationService(importer, store, new OpenApiToolCatalog());
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders.standaloneSetup(new SystemAssistantOpenApiController(new OpenApiSelectionService(mapper, importer)),
                    new SystemAssistantConfigurationController(configurations)).setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
            var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"));
            var base = "/api/system-assistant/configuration";
            mvc.perform(post(base + "/openapi/preview").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("id", "crm", "yaml", yaml))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.operations.length()").value(3))
                    .andExpect(jsonPath("$.specification.openapi").value("3.0.3"))
                    .andExpect(jsonPath("$.specification.paths.length()").value(2));
            var selected = mvc.perform(post(base + "/openapi/selection").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("id", "crm", "yaml", yaml, "operations", List.of(Map.of("method", "GET", "path", "/tickets/{id}"))))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.operationCount").value(1)).andReturn();
            var subset = mapper.readTree(selected.getResponse().getContentAsString()).path("yaml").asText();
            assertThat(subset).doesNotContain("simulateCost", "updateTicket");
            mvc.perform(post(base + "/openapi/document").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("id", "crm", "yaml", subset))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.specification.openapi").value("3.0.3"))
                    .andExpect(jsonPath("$.specification.paths.length()").value(1));
            verify(store, never()).write(any());
            mvc.perform(get(base + "/tools")).andExpect(jsonPath("$.length()").value(0));
            var configuration = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "Demo", subset)), List.of());
            mvc.perform(put(base).contentType("application/json").content(mapper.writeValueAsString(configuration))).andExpect(status().isOk());
            verify(store).write(configuration);
            mvc.perform(get(base + "/tools")).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("oa_crm_getTicket"));
            mvc.perform(post(base + "/openapi/selection").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("id", "crm", "yaml", yaml, "operations", List.of()))))
                    .andExpect(status().isBadRequest());
            assertThat(configurations.current()).isEqualTo(configuration);
        }
    }
}
