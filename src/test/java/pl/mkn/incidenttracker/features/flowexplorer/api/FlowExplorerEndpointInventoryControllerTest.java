package pl.mkn.incidenttracker.features.flowexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointOptionResponse;
import pl.mkn.incidenttracker.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointParameterResponse;
import pl.mkn.incidenttracker.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointSourceResponse;
import pl.mkn.incidenttracker.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointTooltipDetailsResponse;
import pl.mkn.incidenttracker.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.RepositoryInventoryResponse;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSystemNotFoundException;
import pl.mkn.incidenttracker.features.flowexplorer.endpoint.FlowExplorerEndpointInventoryService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlowExplorerEndpointInventoryController.class)
class FlowExplorerEndpointInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowExplorerEndpointInventoryService flowExplorerEndpointInventoryService;

    @Test
    void shouldReturnEndpointInventoryForSystem() throws Exception {
        when(flowExplorerEndpointInventoryService.endpoints(
                "billing-core",
                "feature/FLOW-42",
                "/api",
                "get"
        )).thenReturn(inventory());

        mockMvc.perform(get("/flow-explorer/systems/billing-core/endpoints")
                        .param("branch", "feature/FLOW-42")
                        .param("endpointPathPrefix", "/api")
                        .param("httpMethod", "get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("billing-core"))
                .andExpect(jsonPath("$.requestedBranch").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.resolvedRef").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.gitLabGroup").value("platform/backend"))
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.endpoints", hasSize(1)))
                .andExpect(jsonPath("$.endpoints[0].method").value("GET"))
                .andExpect(jsonPath("$.endpoints[0].path").value("/api/billing/{id}"))
                .andExpect(jsonPath("$.endpoints[0].tooltipDetails.parameters[0].description")
                        .value("customer billing id"));

        verify(flowExplorerEndpointInventoryService).endpoints(
                "billing-core",
                "feature/FLOW-42",
                "/api",
                "get"
        );
    }

    @Test
    void shouldReturnNotFoundWhenSystemDoesNotExist() throws Exception {
        when(flowExplorerEndpointInventoryService.endpoints(
                "missing-system",
                null,
                null,
                null
        )).thenThrow(new FlowExplorerSystemNotFoundException("missing-system"));

        mockMvc.perform(get("/flow-explorer/systems/missing-system/endpoints"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOW_EXPLORER_SYSTEM_NOT_FOUND"));
    }

    private static FlowExplorerEndpointInventoryResponse inventory() {
        var parameter = new EndpointParameterResponse(
                "id",
                "path",
                true,
                "string",
                "customer billing id"
        );
        return new FlowExplorerEndpointInventoryResponse(
                "billing-core",
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                "/api",
                "GET",
                1,
                1,
                1,
                3,
                2,
                false,
                List.of(new RepositoryInventoryResponse(
                        "billing-api",
                        "billing-api",
                        "platform/backend/billing-api",
                        "feature/FLOW-42",
                        3,
                        2,
                        false,
                        1,
                        List.of()
                )),
                List.of(new EndpointOptionResponse(
                        "billing-api:GET /api/billing/{id}",
                        "GET",
                        List.of("GET"),
                        "/api/billing/{id}",
                        "/api/billing/{id}",
                        "Billing lookup",
                        "Returns billing details.",
                        "getBilling",
                        List.of("billing"),
                        "BillingController",
                        "getBilling",
                        new EndpointSourceResponse(
                                "billing-api",
                                "billing-api",
                                "platform/backend/billing-api",
                                "src/main/java/com/example/billing/BillingController.java",
                                12,
                                24
                        ),
                        List.of(parameter),
                        "high",
                        List.of(),
                        List.of("billing-api:src/main/java/com/example/billing/BillingController.java"),
                        new EndpointTooltipDetailsResponse(
                                "OPENAPI_YAML",
                                "Billing lookup",
                                "Returns billing details.",
                                "getBilling",
                                List.of("billing"),
                                List.of(parameter),
                                List.of("@PathVariable String id"),
                                List.of("BillingResponse"),
                                List.of("RestController", "GetMapping"),
                                List.of(),
                                List.of("billing-api:src/main/java/com/example/billing/BillingController.java")
                        )
                )),
                List.of()
        );
    }
}
