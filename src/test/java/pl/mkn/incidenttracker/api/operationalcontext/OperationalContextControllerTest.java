package pl.mkn.incidenttracker.api.operationalcontext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityRelationsReadModelDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelLinkDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelTruncationDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextBlastRadiusReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextImplementationReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;

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
                "active",
                emptyAggregate("Targets"),
                emptyAggregate("Repositories"),
                emptyAggregate("Package hints"),
                emptyAggregate("Entry hints"),
                emptyAggregate("Data hints"),
                emptyAggregate("Workflow hints"),
                emptyAggregate("Strategy"),
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
        when(viewService.entity("repository", "[CLP/backend]"))
                .thenReturn(emptyDetail("repository", "[CLP/backend]"));

        mockMvc.perform(get("/api/operational-context/entities/repository")
                        .param("id", "[CLP/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("repository"))
                .andExpect(jsonPath("$.id").value("[CLP/backend]"));
    }

    @Test
    void shouldExposeReadModelProjectionEndpoints() throws Exception {
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
        when(viewService.implementationReadModel("process", "new-process"))
                .thenReturn(OperationalContextImplementationReadModel.empty(target, List.of()));
        when(viewService.flowReadModel("process", "new-process"))
                .thenReturn(OperationalContextFlowReadModel.empty(target, List.of()));
        when(viewService.blastRadiusReadModel("process", "new-process"))
                .thenReturn(emptyBlastRadius(target));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.entity-relations"))
                .andExpect(jsonPath("$.neighbors[0].id").value("new-system"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/code-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.code-search"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/implementations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.implementation-map"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.flow"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/blast-radius"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.blast-radius"));
    }

    @Test
    void shouldExposeReadModelProjectionEndpointsWithQueryId() throws Exception {
        var target = entityRef("repository", "[CLP/backend]");
        when(viewService.codeSearchReadModel("repository", "[CLP/backend]"))
                .thenReturn(OperationalContextCodeSearchReadModel.empty(target, List.of()));
        when(viewService.blastRadiusReadModel("repository", "[CLP/backend]"))
                .thenReturn(emptyBlastRadius(target));
        when(viewService.blastRadiusReadModel("endpoint", "/new/api"))
                .thenReturn(emptyBlastRadius(entityRef("endpoint", "/new/api")));

        mockMvc.perform(get("/api/operational-context/read-model/entities/repository/code-search")
                        .param("id", "[CLP/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisTarget.id").value("[CLP/backend]"));

        mockMvc.perform(get("/api/operational-context/read-model/entities/repository/blast-radius")
                        .param("id", "[CLP/backend]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.blast-radius"));

        mockMvc.perform(get("/api/operational-context/read-model/blast-radius")
                        .param("type", "endpoint")
                        .param("id", "/new/api"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRouteProfiledReadModelProjectionEndpoints() throws Exception {
        when(viewService.flowReadModel("process", "new-process", "default"))
                .thenReturn(profiled("operational-context.flow", entityRef("process", "new-process")));
        when(viewService.blastRadiusReadModel("class", "NewLimitOrderController", "default"))
                .thenReturn(profiled("operational-context.blast-radius", entityRef("class", "NewLimitOrderController")));

        mockMvc.perform(get("/api/operational-context/read-model/entities/process/new-process/flow")
                        .param("profile", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.flow"))
                .andExpect(jsonPath("$.profile").value("default"));

        mockMvc.perform(get("/api/operational-context/read-model/blast-radius")
                        .param("type", "class")
                        .param("id", "NewLimitOrderController")
                        .param("profile", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("operational-context.blast-radius"))
                .andExpect(jsonPath("$.analysisTarget.id").value("NewLimitOrderController"));
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

    private static OperationalContextBlastRadiusReadModel emptyBlastRadius(EntityRef target) {
        return new OperationalContextBlastRadiusReadModel(
                "operational-context.blast-radius",
                1,
                OperationalContextCodeSearchReadModel.ReadModelProfile.defaultProfile(),
                target,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
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
