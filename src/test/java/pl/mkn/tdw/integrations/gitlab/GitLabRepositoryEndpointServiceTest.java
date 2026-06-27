package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void shouldResolveStaticImportedEndpointAndParameterConstants() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java";
        var constantsPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                12
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.bind.annotation.RestController;

                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_DETAIL_URI;
                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_ID_PARAM;
                        import static com.example.crm.casehandling.api.CustomerCaseUris.INCLUDE_HISTORY_PARAM;

                        @RestController
                        class CustomerCaseController {

                          @GetMapping(CASE_DETAIL_URI)
                          CustomerCaseResponse getCase(@PathVariable(CASE_ID_PARAM) String id,
                                                       @RequestParam(name = INCLUDE_HISTORY_PARAM, required = false)
                                                       boolean withHistory) {
                            return new CustomerCaseResponse(id);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                """
                        package com.example.crm.casehandling.api;

                        public final class CustomerCaseUris {
                          public static final String CASE_ROOT_URI = "/api/crm/customer-cases";
                          public static final String CASE_DETAIL_URI = CASE_ROOT_URI + "/{caseId}";
                          public static final String CASE_ID_PARAM = "caseId";
                          public static final String INCLUDE_HISTORY_PARAM = "includeHistory";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("GET /api/crm/customer-cases/{caseId} -> com.example.crm.casehandling.api.CustomerCaseController#getCase",
                endpoint.endpointId());
        assertEquals("/api/crm/customer-cases/{caseId}", endpoint.path());
        assertEquals(null, endpoint.pathExpression());
        assertTrue(endpoint.limitations().stream()
                .noneMatch(limitation -> limitation.contains("not fully resolved")));
        assertEquals(2, endpoint.documentation().parameters().size());
        assertEquals("caseId", endpoint.documentation().parameters().get(0).name());
        assertEquals("path", endpoint.documentation().parameters().get(0).in());
        assertEquals("includeHistory", endpoint.documentation().parameters().get(1).name());
        assertEquals("query", endpoint.documentation().parameters().get(1).in());
    }

    @Test
    void shouldReadOnlyConstantClassesUsedByEndpointAnnotations() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java";
        var usedConstantsPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";
        var unusedConstantsPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseAuditUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                12
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.bind.annotation.RestController;
                        import com.example.crm.casehandling.api.CustomerCaseAuditUris;
                        import com.example.crm.casehandling.api.CustomerCaseUris;

                        @RestController
                        class CustomerCaseController {

                          @GetMapping(CustomerCaseUris.CASE_DETAIL_URI)
                          CustomerCaseResponse getCase(@RequestParam(name = CustomerCaseUris.INCLUDE_HISTORY_PARAM, required = false)
                                                       boolean withHistory) {
                            return new CustomerCaseResponse("case-1");
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                usedConstantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                usedConstantsPath,
                """
                        package com.example.crm.casehandling.api;

                        public final class CustomerCaseUris {
                          public static final String CASE_ROOT_URI = "/api/crm/customer-cases";
                          public static final String CASE_DETAIL_URI = CASE_ROOT_URI + "/detail";
                          public static final String INCLUDE_HISTORY_PARAM = "includeHistory";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(1, result.endpoints().size());
        assertEquals("/api/crm/customer-cases/detail", result.endpoints().get(0).path());
        assertEquals("includeHistory", result.endpoints().get(0).documentation().parameters().get(0).name());
        verify(repositoryPort, never()).readFile(
                "CRM",
                "crm-case-service",
                "main",
                unusedConstantsPath,
                80_000
        );
        verify(repositoryPort, never()).searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("class CustomerCaseAuditUris")),
                eq(20)
        );
    }

    @Test
    void shouldCacheJavaConstantFilesDuringEndpointInventory() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var firstControllerPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java";
        var secondControllerPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseSearchController.java";
        var constantsPath = "src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(
                new GitLabRepositoryFileCandidate(
                        "CRM",
                        "crm-case-service",
                        "main",
                        firstControllerPath,
                        "Matched @RestController.",
                        12
                ),
                new GitLabRepositoryFileCandidate(
                        "CRM",
                        "crm-case-service",
                        "main",
                        secondControllerPath,
                        "Matched @RestController.",
                        12
                )
        ));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                firstControllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                firstControllerPath,
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_DETAIL_URI;

                        @RestController
                        class CustomerCaseController {

                          @GetMapping(CASE_DETAIL_URI)
                          CustomerCaseResponse getCase(String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                secondControllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                secondControllerPath,
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_SEARCH_URI;

                        @RestController
                        class CustomerCaseSearchController {

                          @GetMapping(CASE_SEARCH_URI)
                          CustomerCaseSearchResponse searchCases() {
                            return new CustomerCaseSearchResponse();
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                """
                        package com.example.crm.casehandling.api;

                        public final class CustomerCaseUris {
                          public static final String CASE_ROOT_URI = "/api/crm/customer-cases";
                          public static final String CASE_DETAIL_URI = CASE_ROOT_URI + "/{caseId}";
                          public static final String CASE_SEARCH_URI = CASE_ROOT_URI + "/search";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(2, result.endpoints().size());
        verify(repositoryPort, times(1)).readFile(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                80_000
        );
    }

    @Test
    void shouldResolveStaticImportedEndpointConstantsFromAnotherModule() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "crm-case/crm-case-service/src/main/java/com/example/crm/casehandling/api/CustomerCaseController.java";
        var wrongSameModuleConstantsPath = "crm-case/crm-case-service/src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";
        var constantsPath = "crm-case/crm-case-api/src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                10
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("class CustomerCaseUris")),
                eq(20)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                "Matched URI constants.",
                3
        )));
        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.casehandling.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_DETAIL_URI;

                        @RestController
                        class CustomerCaseController {

                          @GetMapping(CASE_DETAIL_URI)
                          CustomerCaseResponse getCase(String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                wrongSameModuleConstantsPath,
                80_000
        )).thenThrow(new IllegalStateException("file not found"));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                constantsPath,
                """
                        package com.example.crm.casehandling.api;

                        public final class CustomerCaseUris {
                          public static final String CASE_ROOT_URI = "/api/crm/customer-cases";
                          public static final String CASE_DETAIL_URI = CASE_ROOT_URI + "/{caseId}";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("GET /api/crm/customer-cases/{caseId} -> com.example.crm.casehandling.api.CustomerCaseController#getCase",
                endpoint.endpointId());
        assertEquals("/api/crm/customer-cases/{caseId}", endpoint.path());
        assertEquals(List.of(), endpoint.limitations());
    }

    @Test
    void shouldResolveClassQualifiedEndpointConstantsFromRegularImport() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "crm-exposure/crm-exposure-service/src/main/java/com/example/crm/exposure/api/CustomerExposureController.java";
        var constantsPath = "crm-exposure/crm-exposure-service/src/main/java/com/example/crm/exposure/contract/CustomerExposureUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-exposure-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-exposure-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                9
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-exposure-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.listRepositoryFiles("CRM", "crm-exposure-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-exposure-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-exposure-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.exposure.api;

                        import java.math.BigDecimal;
                        import java.util.List;
                        import java.util.Map;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        import com.example.crm.exposure.contract.CustomerExposureUris;

                        @RestController
                        @RequestMapping(CustomerExposureUris.EXPOSURE_BASE_URI)
                        class CustomerExposureController {

                          @PostMapping
                          Map<String, BigDecimal> getExposure(@RequestBody List<String> customerIds) {
                            return Map.of();
                          }

                          @PostMapping(CustomerExposureUris.TOTAL_EXPOSURE_URI)
                          BigDecimal getTotalExposure(@RequestBody List<String> customerIds) {
                            return BigDecimal.ZERO;
                          }

                          @PostMapping(CustomerExposureUris.EXPOSURE_RECALCULATION_URI)
                          void recalculateExposure(@PathVariable(CustomerExposureUris.PATH_VARIABLE_CASE_ID) String id) {
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-exposure-service",
                "main",
                constantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-exposure-service",
                "main",
                constantsPath,
                """
                        package com.example.crm.exposure.contract;

                        public final class CustomerExposureUris {
                          public static final String EXPOSURE_BASE_URI = "/api/crm/exposures";
                          public static final String TOTAL_EXPOSURE_URI = "/group-total";
                          public static final String PATH_VARIABLE_CASE_ID = "caseId";
                          public static final String EXPOSURE_RECALCULATION_URI = "/{" + PATH_VARIABLE_CASE_ID + "}/recalculate";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-exposure-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(3, result.endpoints().size());
        assertTrue(result.endpoints().stream()
                .anyMatch(endpoint -> "POST /api/crm/exposures -> com.example.crm.exposure.api.CustomerExposureController#getExposure"
                        .equals(endpoint.endpointId())));
        assertTrue(result.endpoints().stream()
                .anyMatch(endpoint -> "POST /api/crm/exposures/group-total -> com.example.crm.exposure.api.CustomerExposureController#getTotalExposure"
                        .equals(endpoint.endpointId())));
        var recalculationEndpoint = result.endpoints().stream()
                .filter(endpoint -> "recalculateExposure".equals(endpoint.handlerMethod()))
                .findFirst()
                .orElseThrow();
        assertEquals("/api/crm/exposures/{caseId}/recalculate", recalculationEndpoint.path());
        assertEquals("caseId", recalculationEndpoint.documentation().parameters().get(0).name());
        assertEquals(List.of(), recalculationEndpoint.limitations());
    }

    @Test
    void shouldResolveClassQualifiedEndpointConstantsFromSamePackage() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "crm-product/crm-product-service/src/main/java/com/example/crm/product/api/CustomerProductController.java";
        var constantsPath = "crm-product/crm-product-service/src/main/java/com/example/crm/product/api/CustomerProductUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-product-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-product-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                14
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-product-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.listRepositoryFiles("CRM", "crm-product-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-product-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-product-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.product.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        class CustomerProductController {

                          @GetMapping(CustomerProductUris.NOT_SUPPORTED_PRODUCTS_URI)
                          Map<String, CustomerProductStatus> getNotSupportedProducts(@RequestParam("customerId") List<String> customerIds) {
                            return Map.of();
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-product-service",
                "main",
                constantsPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-product-service",
                "main",
                constantsPath,
                """
                        package com.example.crm.product.api;

                        public final class CustomerProductUris {
                          static final String PRODUCT_ROOT_URI = "/api/crm/products";
                          public static final String NOT_SUPPORTED_PRODUCTS_URI = PRODUCT_ROOT_URI + "/not-supported-products";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-product-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("GET /api/crm/products/not-supported-products -> com.example.crm.product.api.CustomerProductController#getNotSupportedProducts",
                endpoint.endpointId());
        assertEquals("/api/crm/products/not-supported-products", endpoint.path());
        assertEquals(List.of(), endpoint.limitations());
    }

    @Test
    void shouldResolveTransitiveStaticImportedEndpointConstants() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "crm-case/crm-case-service/src/main/java/com/example/crm/casehandling/lookup/CustomerLookupController.java";
        var sameModuleLookupUrisPath = "crm-case/crm-case-service/src/main/java/com/example/crm/casehandling/lookup/CustomerLookupUris.java";
        var lookupUrisPath = "crm-case/crm-case-api/src/main/java/com/example/crm/casehandling/lookup/CustomerLookupUris.java";
        var caseUrisPath = "crm-case/crm-case-api/src/main/java/com/example/crm/casehandling/api/CustomerCaseUris.java";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                10
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of());
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-case-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("class CustomerLookupUris")),
                eq(20)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-case-service",
                "main",
                lookupUrisPath,
                "Matched lookup URI constants.",
                3
        )));
        when(repositoryPort.listRepositoryFiles("CRM", "crm-case-service", "main", null))
                .thenReturn(List.of());
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.casehandling.lookup;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.PathVariable;
                        import org.springframework.web.bind.annotation.RestController;

                        import static com.example.crm.casehandling.lookup.CustomerLookupUris.LOOKUP_DISTRICT_BY_CODE_URI;

                        @RestController
                        class CustomerLookupController {

                          @GetMapping(LOOKUP_DISTRICT_BY_CODE_URI)
                          CustomerLookupDistrictResponse getDistrict(@PathVariable String districtCode) {
                            return new CustomerLookupDistrictResponse(districtCode);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                sameModuleLookupUrisPath,
                80_000
        )).thenThrow(new IllegalStateException("file not found"));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                lookupUrisPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                lookupUrisPath,
                """
                        package com.example.crm.casehandling.lookup;

                        import static com.example.crm.casehandling.api.CustomerCaseUris.CASE_ROOT_URI;

                        public final class CustomerLookupUris {
                          public static final String LOOKUP_ROOT_URI = CASE_ROOT_URI + "/lookups";
                          public static final String LOOKUP_DISTRICTS_URI = LOOKUP_ROOT_URI + "/districts";
                          public static final String LOOKUP_DISTRICT_BY_CODE_URI = LOOKUP_DISTRICTS_URI + "/{districtCode}";
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                caseUrisPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                caseUrisPath,
                """
                        package com.example.crm.casehandling.api;

                        public final class CustomerCaseUris {
                          public static final String CASE_ROOT_URI = "/api/crm/customer-cases";
                        }
                        """,
                false
        ));

        var result = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM",
                "crm-case-service",
                "main",
                null,
                null,
                20
        ));

        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("GET /api/crm/customer-cases/lookups/districts/{districtCode} -> com.example.crm.casehandling.lookup.CustomerLookupController#getDistrict",
                endpoint.endpointId());
        assertEquals("/api/crm/customer-cases/lookups/districts/{districtCode}", endpoint.path());
        assertEquals(List.of(), endpoint.limitations());
    }

    @Test
    void shouldDiscoverEndpointCandidatesBySpringRestSignalsBeforeRepositoryTreeFallback() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var endpointService = new GitLabRepositoryEndpointService(repositoryPort);
        var controllerPath = "src/main/java/com/example/crm/customer/api/CustomerCaseController.java";
        var openApiPath = "src/main/resources/openapi/customer-case-api.yml";

        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-customer-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("@RestController")),
                eq(100)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-customer-service",
                "main",
                controllerPath,
                "Matched @RestController.",
                10
        )));
        when(repositoryPort.searchRepositoryFilesByContent(
                eq("CRM"),
                eq("crm-customer-service"),
                eq("main"),
                argThat(terms -> terms != null && terms.contains("openapi:")),
                eq(50)
        )).thenReturn(List.of(new GitLabRepositoryFileCandidate(
                "CRM",
                "crm-customer-service",
                "main",
                openApiPath,
                "Matched openapi manifest.",
                10
        )));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-service",
                "main",
                controllerPath,
                80_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-service",
                "main",
                controllerPath,
                """
                        package com.example.crm.customer.api;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api/crm/customer-cases")
                        class CustomerCaseController {

                          @GetMapping("/{caseId}")
                          CustomerCaseResponse getCase(String caseId) {
                            return new CustomerCaseResponse(caseId);
                          }
                        }
                        """,
                false
        ));
        when(repositoryPort.readFile(
                "CRM",
                "crm-customer-service",
                "main",
                openApiPath,
                260_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-customer-service",
                "main",
                openApiPath,
                """
                        openapi: 3.0.1
                        info:
                          title: CRM Customer Case API
                          version: 1.0.0
                        paths:
                          /api/crm/customer-cases/{caseId}:
                            get:
                              summary: Pobranie sprawy klienta CRM
                              operationId: getCase
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

        assertEquals(1, result.candidateFileCount());
        assertEquals(1, result.scannedFileCount());
        assertEquals(1, result.endpoints().size());
        assertEquals("/api/crm/customer-cases/{caseId}", result.endpoints().get(0).path());
        assertTrue(result.limitations().stream()
                .noneMatch(limitation -> limitation.contains("production Java source files")));
        verify(repositoryPort, never()).listRepositoryFiles("CRM", "crm-customer-service", "main", null);
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
