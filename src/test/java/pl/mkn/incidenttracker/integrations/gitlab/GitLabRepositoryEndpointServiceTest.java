package pl.mkn.incidenttracker.integrations.gitlab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabRepositoryEndpointServiceTest {

    private final GitLabRepositoryEndpointService service = new GitLabRepositoryEndpointService(
            mock(GitLabRepositoryPort.class)
    );

    @Test
    void shouldReturnOpenApiYamlBackedEndpointsWithPathAndControllerImplementation() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var javaFile = new GitLabRepositoryFile(
                "CRM",
                "crm-customer-service",
                "main",
                "src/main/java/com/example/crm/customer/adapter/in/rest/CustomerDataController.java"
        );
        var yamlFile = new GitLabRepositoryFile(
                "CRM",
                "crm-customer-service",
                "main",
                "src/main/resources/openapi/customer_data_api.yaml"
        );

        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", "src/main/java"))
                .thenReturn(List.of(javaFile));
        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", null))
                .thenReturn(List.of(javaFile, yamlFile));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-service",
                "main",
                javaFile.filePath(),
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-service",
                "main",
                javaFile.filePath(),
                """
                        package com.example.crm.customer.adapter.in.rest;

                        import java.util.UUID;
                        import org.springframework.http.ResponseEntity;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        class CustomerDataController implements CustomerDataApi {

                          @Override
                          public ResponseEntity<CustomerModel> getCustomer(UUID customerId) {
                            return ResponseEntity.ok(new CustomerModel(customerId));
                          }

                          @Override
                          public ResponseEntity<CustomerModel> updateCustomer(UUID customerId,
                                                                                 CustomerModel customer) {
                            return ResponseEntity.ok(customer);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-service",
                "main",
                yamlFile.filePath(),
                260_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-service",
                "main",
                yamlFile.filePath(),
                """
                        openapi: 3.0.1
                        info:
                          title: CRM Customer Data API
                          version: 1.0.0
                        paths:
                          /api/crm/customers/{customerId}:
                            get:
                              tags:
                                - CustomerData
                              summary: Pobranie klienta CRM
                              description: Zwraca dane klienta na potrzeby widoku profilu.
                              operationId: getCustomer
                              parameters:
                                - name: customerId
                                  in: path
                                  required: true
                                  description: Identyfikator klienta CRM.
                                  schema:
                                    type: string
                                    format: uuid
                            put:
                              tags:
                                - CustomerData
                              summary: Aktualizacja klienta CRM
                              operationId: updateCustomer
                              parameters:
                                - name: customerId
                                  in: path
                                  required: true
                                  schema:
                                    type: string
                                    format: uuid
                                - name: dryRun
                                  in: query
                                  required: false
                                  description: Weryfikuje zmianę bez zapisu.
                                  schema:
                                    type: boolean
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-customer-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(2, result.endpoints().size());
        assertEquals("GET /api/crm/customers/{customerId} -> com.example.crm.customer.adapter.in.rest.CustomerDataController#getCustomer",
                result.endpoints().get(0).endpointId());
        assertEquals("/api/crm/customers/{customerId}", result.endpoints().get(0).path());
        assertTrue(result.endpoints().get(0).annotations().contains("OpenApiContract"));
        assertTrue(result.endpoints().get(0).annotations().contains("Implements CustomerDataApi"));
        assertTrue(result.endpoints().get(0).limitations()
                .contains("Endpoint mapping resolved from OpenAPI YAML contract, not Java annotations."));
        assertEquals("OPENAPI_YAML", result.endpoints().get(0).documentation().source());
        assertEquals("Pobranie klienta CRM", result.endpoints().get(0).documentation().summary());
        assertEquals("Zwraca dane klienta na potrzeby widoku profilu.",
                result.endpoints().get(0).documentation().description());
        assertEquals("customerId", result.endpoints().get(0).documentation().parameters().get(0).name());
        assertEquals("path", result.endpoints().get(0).documentation().parameters().get(0).in());
        assertEquals("string(uuid)", result.endpoints().get(0).documentation().parameters().get(0).type());
        assertTrue(result.endpoints().get(0).suggestedNextReads().stream()
                .anyMatch(nextRead -> nextRead.contains(yamlFile.filePath())));
        assertEquals("PUT /api/crm/customers/{customerId} -> com.example.crm.customer.adapter.in.rest.CustomerDataController#updateCustomer",
                result.endpoints().get(1).endpointId());
        assertEquals(2, result.endpoints().get(1).documentation().parameters().size());
        assertEquals(false, result.endpoints().get(1).documentation().parameters().get(1).required());
    }

    @Test
    void shouldReturnOpenApiAnnotationDocumentationForControllerEndpoint() {
        var endpoints = service.parseEndpointFile(
                "crm-customer-service",
                "src/main/java/com/example/crm/customer/api/CustomerController.java",
                """
                        package com.example.crm.customer.api;

                        import io.swagger.v3.oas.annotations.Operation;
                        import io.swagger.v3.oas.annotations.Parameter;
                        import io.swagger.v3.oas.annotations.enums.ParameterIn;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api/crm/customers")
                        public class CustomerController {

                          @Operation(
                              summary = "Pobranie klienta",
                              description = "Zwraca klienta wraz z aktywnym segmentem CRM.",
                              operationId = "getCustomer",
                              tags = {"Customer"})
                          @GetMapping("/{customerId}")
                          CustomerResponse getCustomer(
                              @Parameter(description = "Identyfikator klienta.", in = ParameterIn.PATH)
                              @PathVariable("customerId") String customerId,
                              @RequestParam(name = "includeInactive", required = false) boolean includeInactive) {
                            return new CustomerResponse(customerId);
                          }
                        }
                        """,
                List.of()
        );

        assertEquals(1, endpoints.size());
        var documentation = endpoints.get(0).documentation();
        assertEquals("JAVA_OPENAPI_ANNOTATION", documentation.source());
        assertEquals("Pobranie klienta", documentation.summary());
        assertEquals("Zwraca klienta wraz z aktywnym segmentem CRM.", documentation.description());
        assertEquals("getCustomer", documentation.operationId());
        assertEquals(List.of("Customer"), documentation.tags());
        assertEquals(2, documentation.parameters().size());
        assertEquals("customerId", documentation.parameters().get(0).name());
        assertEquals("path", documentation.parameters().get(0).in());
        assertEquals("Identyfikator klienta.", documentation.parameters().get(0).description());
        assertEquals("includeInactive", documentation.parameters().get(1).name());
        assertEquals("query", documentation.parameters().get(1).in());
        assertEquals(false, documentation.parameters().get(1).required());
    }

    @Test
    void shouldCacheEndpointInventoryAcrossDifferentFilters() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(
                repositoryPort,
                new GitLabRepositoryAnalysisCache()
        );
        var controllerFile = new GitLabRepositoryFile(
                "CRM",
                "crm-case-service",
                "main",
                "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java"
        );

        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of(controllerFile));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerFile.filePath(),
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                controllerFile.filePath(),
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api/crm/customer-cases")
                        class CustomerCaseController {

                          @GetMapping("/{caseId}")
                          CustomerCaseResponse getCase(String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }

                          @PostMapping
                          CustomerCaseResponse createCase(CustomerCaseRequest request) {
                            return new CustomerCaseResponse("new");
                          }
                        }
                        """,
                false
        ));

        var allEndpoints = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));
        var getEndpoints = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                "/api/crm/customer-cases",
                "GET",
                20
        ));

        assertEquals(2, allEndpoints.endpoints().size());
        assertEquals(1, getEndpoints.endpoints().size());
        assertEquals("GET", getEndpoints.endpoints().get(0).httpMethods().get(0));
        verify(repositoryPort, times(1)).listRepositoryFiles("CRM", "crm-case-service", "main", null);
        verify(repositoryPort, times(1)).readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerFile.filePath(),
                80_000
        );
    }

    @Test
    void shouldIgnoreTopLevelFeignClientMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-case-service",
                "src/main/java/com/example/crm/casehandling/adapter/out/rest/CrmTaskClient.java",
                """
                        package com.example.crm.casehandling.adapter.out.rest;

                        import java.util.List;
                        import java.util.Map;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.http.ResponseEntity;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.PutMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestParam;

                        @FeignClient(url = "${integration.crm.url}")
                        interface CrmTaskClient {

                          @PutMapping("/crm/tasks/{task-id}/complete")
                          void completeInteractionTask(@PathVariable("task-id") String id,
                                                       @RequestBody Map<String, String> resolution);

                          @PostMapping("/crm/tasks/search-by-owner")
                          List<CustomerTaskDto> findTasksForOwner(
                              @RequestBody CustomerOwnerContextDto context,
                              @RequestParam(name = "onlyCurrentAdvisor") boolean onlyCurrentAdvisor);
                        }
                        """,
                List.of()
        );

        assertEquals(List.of(), endpoints);
    }

    @Test
    void shouldIgnoreNestedFeignClientMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-customer-service",
                "src/main/java/com/example/crm/customer/adapter/out/external/segmentation/CustomerSegmentIntegrationService.java",
                """
                        package com.example.crm.customer.adapter.out.external.segmentation;

                        import java.util.List;
                        import lombok.RequiredArgsConstructor;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.web.bind.annotation.GetMapping;

                        @AdapterBean
                        @RequiredArgsConstructor
                        public class CustomerSegmentIntegrationService {

                          @FeignClient(url = "${integration.crm-data.url}")
                          @FunctionalInterface
                          interface CustomerSegmentationClient {

                            @GetMapping("/crm/customer-segments/active")
                            List<CustomerSegmentModel> getActiveSegments();
                          }
                        }
                        """,
                List.of()
        );

        assertEquals(List.of(), endpoints);
    }

    @Test
    void shouldKeepRestControllerMappings() {
        var endpoints = service.parseEndpointFile(
                "crm-case-api",
                "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java",
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api/crm/customer-cases")
                        public class CustomerCaseController {

                          @GetMapping("/{caseId}")
                          CustomerCaseResponse getCase(@PathVariable String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }
                        }
                        """,
                List.of()
        );

        assertEquals(1, endpoints.size());
        assertEquals("GET /api/crm/customer-cases/{caseId} -> com.example.crm.casehandling.api.CustomerCaseController#getCase",
                endpoints.get(0).endpointId());
    }
}
