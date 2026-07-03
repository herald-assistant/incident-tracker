package pl.mkn.tdw.features.flowexplorer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointOptionResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointParameterResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointSourceResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointTooltipDetailsResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.RepositoryInventoryResponse;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSystemNotFoundException;
import pl.mkn.tdw.features.flowexplorer.endpoint.FlowExplorerEndpointInventoryService;

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
                "catalog-core",
                "feature/FLOW-42",
                "/api",
                "get",
                false
        )).thenReturn(inventory());

        mockMvc.perform(get("/api/flow-explorer/systems/catalog-core/endpoints")
                        .param("branch", "feature/FLOW-42")
                        .param("endpointPathPrefix", "/api")
                        .param("httpMethod", "get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("catalog-core"))
                .andExpect(jsonPath("$.requestedBranch").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.resolvedRef").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.gitLabGroup").value("platform/backend"))
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.endpoints", hasSize(1)))
                .andExpect(jsonPath("$.endpoints[0].method").value("GET"))
                .andExpect(jsonPath("$.endpoints[0].path").value("/api/catalog/{id}"))
                .andExpect(jsonPath("$.endpoints[0].tooltipDetails.parameters[0].description")
                        .value("catalog item id"));

        verify(flowExplorerEndpointInventoryService).endpoints(
                "catalog-core",
                "feature/FLOW-42",
                "/api",
                "get",
                false
        );
    }

    @Test
    void shouldPassRefreshFlagToEndpointInventoryService() throws Exception {
        when(flowExplorerEndpointInventoryService.endpoints(
                "catalog-core",
                "main",
                null,
                null,
                true
        )).thenReturn(inventory());

        mockMvc.perform(get("/api/flow-explorer/systems/catalog-core/endpoints")
                        .param("branch", "main")
                        .param("refresh", "true"))
                .andExpect(status().isOk());

        verify(flowExplorerEndpointInventoryService).endpoints(
                "catalog-core",
                "main",
                null,
                null,
                true
        );
    }

    @Test
    void shouldReturnNotFoundWhenSystemDoesNotExist() throws Exception {
        when(flowExplorerEndpointInventoryService.endpoints(
                "missing-system",
                null,
                null,
                null,
                false
        )).thenThrow(new FlowExplorerSystemNotFoundException("missing-system"));

        mockMvc.perform(get("/api/flow-explorer/systems/missing-system/endpoints"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOW_EXPLORER_SYSTEM_NOT_FOUND"));
    }

    private static FlowExplorerEndpointInventoryResponse inventory() {
        var parameter = new EndpointParameterResponse(
                "id",
                "path",
                true,
                "string",
                "catalog item id"
        );
        return new FlowExplorerEndpointInventoryResponse(
                "catalog-core",
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
                        "catalog-api",
                        "catalog-api",
                        "platform/backend/catalog-api",
                        "feature/FLOW-42",
                        3,
                        2,
                        false,
                        1,
                        List.of()
                )),
                List.of(new EndpointOptionResponse(
                        "catalog-api:GET /api/catalog/{id}",
                        "GET",
                        List.of("GET"),
                        "/api/catalog/{id}",
                        "/api/catalog/{id}",
                        "Catalog lookup",
                        "Returns catalog details.",
                        "getCatalog",
                        List.of("catalog"),
                        "CatalogController",
                        "getCatalog",
                        new EndpointSourceResponse(
                                "catalog-api",
                                "catalog-api",
                                "platform/backend/catalog-api",
                                "src/main/java/com/example/catalog/CatalogController.java",
                                12,
                                24
                        ),
                        List.of(parameter),
                        "high",
                        List.of(),
                        List.of("catalog-api:src/main/java/com/example/catalog/CatalogController.java"),
                        new EndpointTooltipDetailsResponse(
                                "OPENAPI_YAML",
                                "Catalog lookup",
                                "Returns catalog details.",
                                "getCatalog",
                                List.of("catalog"),
                                List.of(parameter),
                                List.of("@PathVariable String id"),
                                List.of("CatalogResponse"),
                                List.of("RestController", "GetMapping"),
                                List.of(),
                                List.of("catalog-api:src/main/java/com/example/catalog/CatalogController.java")
                        )
                )),
                List.of()
        );
    }
}
