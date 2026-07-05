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
                "crm-customer-profile",
                "feature/FLOW-42",
                "/api",
                "get",
                false
        )).thenReturn(inventory());

        mockMvc.perform(get("/api/flow-explorer/systems/crm-customer-profile/endpoints")
                        .param("branch", "feature/FLOW-42")
                        .param("endpointPathPrefix", "/api")
                        .param("httpMethod", "get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("crm-customer-profile"))
                .andExpect(jsonPath("$.requestedBranch").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.resolvedRef").value("feature/FLOW-42"))
                .andExpect(jsonPath("$.gitLabGroup").value("platform/backend"))
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.endpoints", hasSize(1)))
                .andExpect(jsonPath("$.endpoints[0].method").value("GET"))
                .andExpect(jsonPath("$.endpoints[0].path").value("/api/crm/customers/{customerId}/profile"))
                .andExpect(jsonPath("$.endpoints[0].tooltipDetails.parameters[0].description")
                        .value("customer profile id"));

        verify(flowExplorerEndpointInventoryService).endpoints(
                "crm-customer-profile",
                "feature/FLOW-42",
                "/api",
                "get",
                false
        );
    }

    @Test
    void shouldPassRefreshFlagToEndpointInventoryService() throws Exception {
        when(flowExplorerEndpointInventoryService.endpoints(
                "crm-customer-profile",
                "main",
                null,
                null,
                true
        )).thenReturn(inventory());

        mockMvc.perform(get("/api/flow-explorer/systems/crm-customer-profile/endpoints")
                        .param("branch", "main")
                        .param("refresh", "true"))
                .andExpect(status().isOk());

        verify(flowExplorerEndpointInventoryService).endpoints(
                "crm-customer-profile",
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
                "customer profile id"
        );
        return new FlowExplorerEndpointInventoryResponse(
                "crm-customer-profile",
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
                        "crm-customer-profile-api",
                        "crm-customer-profile-api",
                        "platform/backend/crm-customer-profile-api",
                        "feature/FLOW-42",
                        3,
                        2,
                        false,
                        1,
                        List.of()
                )),
                List.of(new EndpointOptionResponse(
                        "crm-customer-profile-api:GET /api/crm/customers/{customerId}/profile",
                        "GET",
                        List.of("GET"),
                        "/api/crm/customers/{customerId}/profile",
                        "/api/crm/customers/{customerId}/profile",
                        "Customer profile lookup",
                        "Returns customer profile details.",
                        "getCustomerProfile",
                        List.of("crm-customer-profile"),
                        "CustomerProfileController",
                        "getCustomerProfile",
                        new EndpointSourceResponse(
                                "crm-customer-profile-api",
                                "crm-customer-profile-api",
                                "platform/backend/crm-customer-profile-api",
                                "src/main/java/com/example/crm/customerprofile/CustomerProfileController.java",
                                12,
                                24
                        ),
                        List.of(parameter),
                        "high",
                        List.of(),
                        List.of("crm-customer-profile-api:src/main/java/com/example/crm/customerprofile/CustomerProfileController.java"),
                        new EndpointTooltipDetailsResponse(
                                "OPENAPI_YAML",
                                "Customer profile lookup",
                                "Returns customer profile details.",
                                "getCustomerProfile",
                                List.of("crm-customer-profile"),
                                List.of(parameter),
                                List.of("@PathVariable String customerId"),
                                List.of("CustomerProfileResponse"),
                                List.of("RestController", "GetMapping"),
                                List.of(),
                                List.of("crm-customer-profile-api:src/main/java/com/example/crm/customerprofile/CustomerProfileController.java")
                        )
                )),
                List.of()
        );
    }
}
