package pl.mkn.incidenttracker.api.operationalcontext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;

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
                0,
                Map.of("info", 0, "warning", 0, "error", 0),
                "ready",
                List.of()
        ));

        mockMvc.perform(get("/api/operational-context/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systems").value(1))
                .andExpect(jsonPath("$.catalogStatus").value("ready"))
                .andExpect(jsonPath("$.validationFindings.error").value(0));
    }

    @Test
    void shouldExposeSystemsEndpoint() throws Exception {
        when(viewService.systems()).thenReturn(List.of(new OperationalContextSystemRowDto(
                "app-core",
                "App Core",
                "internal",
                null,
                "Runs core flow",
                emptyAggregate("Repositories"),
                emptyAggregate("Processes"),
                emptyAggregate("Contexts"),
                emptyAggregate("Integrations"),
                emptyAggregate("Signals"),
                emptyAggregate("Handoff"),
                emptyAggregate("Validation"),
                emptyAggregate("Open questions")
        )));

        mockMvc.perform(get("/api/operational-context/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("app-core"))
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
}
