package pl.mkn.tdw.api.operationalcontext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityRelationsReadModelDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelLinkDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelTruncationDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextResolvedOwnershipDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalContextController.class)
class OperationalContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationalContextViewService viewService;

    @Test
    void shouldExposeSummaryEndpoint() throws Exception {
        when(viewService.summary()).thenReturn(new OperationalContextSummaryDto(
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                0,
                Map.of("info", 0, "warning", 0, "error", 0),
                "ready",
                List.of()
        ));

        mockMvc.perform(get("/api/operational-context/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systems").value(1))
                .andExpect(jsonPath("$.codeSearchScopes").value(1))
                .andExpect(jsonPath("$.catalogStatus").value("ready"))
                .andExpect(jsonPath("$.validationFindings.error").value(0));
    }

    @Test
    void shouldRouteProfiledSummaryToCompactEnvelope() throws Exception {
        when(viewService.summary("default")).thenReturn(profiled("operational-context.summary", null));

        mockMvc.perform(get("/api/operational-context/summary")
                        .param("profile", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.summary"))
                .andExpect(jsonPath("$.profile").value("default"))
                .andExpect(jsonPath("$.links[0].rel").value("expanded"))
                .andExpect(jsonPath("$.availableExpansions[0]").value("profile=expanded"));
    }

    @Test
    void shouldExposeSystemsEndpoint() throws Exception {
        when(viewService.systems()).thenReturn(List.of(new OperationalContextSystemRowDto(
                "app-core",
                "App Core",
                "internal",
                null,
                emptyResolvedOwnership(),
                "Runs core flow",
                emptyAggregate("Relations"),
                emptyAggregate("Signals"),
                emptyAggregate("Handoff"),
                emptyAggregate("Validation"),
                emptyAggregate("Open questions")
        )));

        mockMvc.perform(get("/api/operational-context/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("app-core"))
                .andExpect(jsonPath("$[0].relations.count").value(0));
    }

    @Test
    void shouldExposeCodeSearchScopesEndpoint() throws Exception {
        when(viewService.codeSearchScopes()).thenReturn(List.of(new OperationalContextCodeSearchScopeRowDto(
                "app-core-scope",
                "App Core Scope",
                "system",
                "active",
                emptyAggregate("Target"),
                emptyAggregate("Repositories"),
                emptyAggregate("Limitations"),
                emptyAggregate("Validation")
        )));

        mockMvc.perform(get("/api/operational-context/code-search-scopes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("app-core-scope"))
                .andExpect(jsonPath("$[0].repositories.count").value(0));
    }

    @Test
    void shouldReturnControlledNotFoundForMissingEntity() throws Exception {
        when(viewService.entity("system", "missing"))
                .thenThrow(new OperationalContextEntityNotFoundException("system", "missing"));

        mockMvc.perform(get("/api/operational-context/entities/system/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATIONAL_CONTEXT_ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldExposeEntityEndpointWithQueryId() throws Exception {
        when(viewService.entity("repository", "[CRM/backend]"))
                .thenReturn(emptyDetail("repository", "[CRM/backend]"));

        mockMvc.perform(get("/api/operational-context/entities/repository")
                        .param("id", "[CRM/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("repository"))
                .andExpect(jsonPath("$.id").value("[CRM/backend]"));
    }

    @Test
    void shouldExposeCurrentReadModelProjectionEndpoints() throws Exception {
        var target = entityRef("process", "new-process");
        when(viewService.entityRelationsReadModel("process", "new-process"))
                .thenReturn(new OperationalContextEntityRelationsReadModelDto(
                        "operational-context.entity-relations",
                        1,
                        target,
                        List.of(),
                        List.of(),
                        List.of(entityRef("system", "new-system")),
                        List.of()
                ));
        when(viewService.codeSearchReadModel("process", "new-process"))
                .thenReturn(OperationalContextCodeSearchReadModel.empty(target, List.of()));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.entity-relations"))
                .andExpect(jsonPath("$.neighbors[0].id").value("new-system"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/code-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.code-search"));
    }

    @Test
    void shouldExposeCurrentReadModelProjectionEndpointsWithQueryId() throws Exception {
        var target = entityRef("repository", "[CRM/backend]");
        when(viewService.entityRelationsReadModel("repository", "[CRM/backend]"))
                .thenReturn(new OperationalContextEntityRelationsReadModelDto(
                        "operational-context.entity-relations",
                        1,
                        target,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));
        when(viewService.codeSearchReadModel("repository", "[CRM/backend]"))
                .thenReturn(OperationalContextCodeSearchReadModel.empty(target, List.of()));

        mockMvc.perform(get("/api/operational-context/read-model/entities/repository/relations")
                        .param("id", "[CRM/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisTarget.id").value("[CRM/backend]"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/repository/code-search")
                        .param("id", "[CRM/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisTarget.id").value("[CRM/backend]"));
    }

    @Test
    void shouldRouteProfiledReadModelProjectionEndpoints() throws Exception {
        when(viewService.entityRelationsReadModel("process", "new-process", "default"))
                .thenReturn(profiled("operational-context.entity-relations", entityRef("process", "new-process")));
        when(viewService.codeSearchReadModel("process", "new-process", "default"))
                .thenReturn(profiled("operational-context.code-search", entityRef("process", "new-process")));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/relations")
                        .param("profile", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.entity-relations"))
                .andExpect(jsonPath("$.profile").value("default"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/code-search")
                        .param("profile", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.code-search"))
                .andExpect(jsonPath("$.profile").value("default"));
    }

    private static ExplainableAggregateDto emptyAggregate(String label) {
        return new ExplainableAggregateDto(
                label,
                0,
                "unknown",
                "high",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of()
        );
    }

    private static OperationalContextResolvedOwnershipDto emptyResolvedOwnership() {
        return new OperationalContextResolvedOwnershipDto(
                "unknown",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of()
        );
    }

    private static OperationalContextEntityDetailDto emptyDetail(String type, String id) {
        return new OperationalContextEntityDetailDto(
                type,
                id,
                id,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static EntityRef entityRef(String type, String id) {
        return new EntityRef(type, id, id, null, null);
    }

    private static OperationalContextProfiledReadModelDto profiled(String contract, EntityRef target) {
        return new OperationalContextProfiledReadModelDto(
                contract,
                1,
                "default",
                target,
                Map.of("returned", 1),
                List.of(new OperationalContextReadModelLinkDto("expanded", "/expanded", "expanded", "Full payload.")),
                List.of("profile=expanded"),
                List.of("Read the compact entity before expanding."),
                List.of(),
                List.of("opctx_get_entity"),
                "Expand only for full diagnostics.",
                List.of(),
                new OperationalContextReadModelTruncationDto(false, null, Map.of("returned", 1), Map.of()),
                1.0,
                "high",
                List.of(),
                null,
                List.of(),
                List.of()
        );
    }
}
