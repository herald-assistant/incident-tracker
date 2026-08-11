package pl.mkn.tdw.api.operationalcontext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationResult;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDeleteImpact;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEditableEntity;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextSnapshot;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextStoreException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalContextMaintenanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class OperationalContextMaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationalContextCatalogMaintenanceService maintenanceService;

    @MockitoBean
    private OperationalContextPort operationalContextPort;

    @BeforeEach
    void setUp() {
        when(operationalContextPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot(
                "crm-content-digest", "tdw-data/operational-context", OperationalContextCatalog.empty()
        ));
    }

    @Test
    void shouldExposeSingleLocalCopyAndAllYamlTypes() throws Exception {
        mockMvc.perform(get("/api/operational-context/catalog/capabilities"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.source").value("tdw-data/operational-context"))
                .andExpect(jsonPath("$.supportedEntityTypes.length()").value(9))
                .andExpect(jsonPath("$.writable").doesNotExist())
                .andExpect(jsonPath("$.storageMode").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.reasonCodes").doesNotExist());
    }

    @Test
    void shouldCreateEachYamlTypeWithoutSecurityOrRevisionHeaders() throws Exception {
        when(maintenanceService.create(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationCommand.class);
            var entity = new OperationalContextEditableEntity(
                    command.type(), command.id(), sourceFile(command.type()), command.payload()
            );
            return new OperationalContextCatalogMutationResult(entity);
        });

        for (var type : OperationalContextCatalogEntityType.values()) {
            var id = "crm-anonymous-" + type.externalName();
            mockMvc.perform(post("/api/operational-context/catalog/entities/{type}", type.externalName())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(writeBody(type.externalName(), id)))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost/api/operational-context/catalog/entities/" + type.externalName() + "/" + id))
                    .andExpect(jsonPath("$.entity.type").value(type.externalName()))
                    .andExpect(jsonPath("$.entity.id").value(id))
                    .andExpect(jsonPath("$.entity.payload.rawSourcePreview").doesNotExist());
        }
    }

    @Test
    void shouldExposeEditableEntityCompletePutDeleteImpactAndDelete() throws Exception {
        var entity = new OperationalContextEditableEntity(
                "team", "crm-operations-team", "teams.yml",
                Map.of("id", "crm-operations-team", "name", "CRM Operations Team")
        );
        when(maintenanceService.entity("team", "crm-operations-team")).thenReturn(entity);
        when(maintenanceService.update(any())).thenReturn(new OperationalContextCatalogMutationResult(
                new OperationalContextEditableEntity(
                        "team", "crm-operations-team", "teams.yml",
                        Map.of("id", "crm-operations-team", "name", "CRM Operations Team Updated")
                )
        ));
        when(maintenanceService.deleteImpact("team", "crm-operations-team")).thenReturn(new OperationalContextDeleteImpact(
                "team", "crm-operations-team", "teams.yml", true, List.of()
        ));
        when(maintenanceService.delete("team", "crm-operations-team")).thenReturn(
                new OperationalContextSnapshot("crm-content-after-delete", "tdw-data/operational-context", OperationalContextCatalog.empty())
        );

        mockMvc.perform(get("/api/operational-context/catalog/entities/team/crm-operations-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFile").value("teams.yml"))
                .andExpect(jsonPath("$.payload.name").value("CRM Operations Team"));

        mockMvc.perform(put("/api/operational-context/catalog/entities/team/crm-operations-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("team", "crm-operations-team")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity.payload.name").value("CRM Operations Team Updated"));

        mockMvc.perform(get("/api/operational-context/catalog/entities/team/crm-operations-team/delete-impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.inboundReferences.length()").value(0));

        mockMvc.perform(delete("/api/operational-context/catalog/entities/team/crm-operations-team"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldMapDomainValidationAndLocalCopyAvailability() throws Exception {
        when(maintenanceService.create(any())).thenThrow(new OperationalContextCatalogMaintenanceException(
                OperationalContextCatalogMaintenanceException.Code.VALIDATION_FAILED,
                "Anonymous CRM payload is invalid",
                List.of(new OperationalContextCatalogFieldError("/payload/name", "Name is required"))
        ));
        mockMvc.perform(post("/api/operational-context/catalog/entities/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("team", "crm-anonymous-team")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("/payload/name"));

        when(maintenanceService.entity(eq("team"), eq("crm-operations-team"))).thenThrow(new OperationalContextStoreException(
                OperationalContextStoreException.Code.CORRUPT_STORE,
                "Anonymous CRM local copy is unavailable"
        ));
        mockMvc.perform(get("/api/operational-context/catalog/entities/team/crm-operations-team"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CORRUPT_STORE"));
    }

    @Test
    void shouldRejectPathEnvelopeMismatch() throws Exception {
        mockMvc.perform(put("/api/operational-context/catalog/entities/team/crm-operations-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("system", "crm-operations-team")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ID_MISMATCH"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("/type"));
    }

    private String writeBody(String type, String id) {
        var displayPayload = switch (type) {
            case "glossary-term" -> "\"term\": \"Anonymous CRM term\", \"category\": \"domain-term\"";
            case "handoff-rule" -> "\"title\": \"Anonymous CRM handoff rule\"";
            default -> "\"name\": \"Anonymous CRM entity\"";
        };
        return """
                {
                  "type": "%s",
                  "id": "%s",
                  "payload": {
                    "id": "%s",
                    %s
                  }
                }
                """.formatted(type, id, id, displayPayload);
    }

    private String sourceFile(String type) {
        return switch (type) {
            case "system" -> "systems.yml";
            case "repository" -> "repo-map.yml";
            case "code-search-scope" -> "code-search-scopes.yml";
            case "process" -> "processes.yml";
            case "integration" -> "integrations.yml";
            case "bounded-context" -> "bounded-contexts.yml";
            case "team" -> "teams.yml";
            case "glossary-term" -> "glossary.yml";
            case "handoff-rule" -> "handoff-rules.yml";
            default -> throw new IllegalArgumentException(type);
        };
    }
}
