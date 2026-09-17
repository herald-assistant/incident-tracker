package pl.mkn.tdw.integrations.gitlab.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabOpenApiEndpointSliceServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabOpenApiEndpointSliceService service = new GitLabOpenApiEndpointSliceService(
            repositoryPort,
            new ObjectMapper()
    );

    @Test
    void shouldReturnOnlyRequestedEndpointOperationWithReferencedSchemas() {
        when(repositoryPort.readFile(
                "CRM",
                "crm-case-service",
                "main",
                "src/main/resources/openapi/customer-case-api.yaml",
                500_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-case-service",
                "main",
                "src/main/resources/openapi/customer-case-api.yaml",
                """
                        openapi: 3.0.1
                        info:
                          title: CRM Customer Case API
                          version: 1.0.0
                        paths:
                          /api/crm/customers/{customerId}/cases:
                            post:
                              tags:
                                - CustomerCase
                              summary: Utworzenie sprawy klienta
                              description: Tworzy sprawe klienta w obsludze CRM.
                              operationId: createCustomerCase
                              parameters:
                                - name: customerId
                                  in: path
                                  required: true
                                  schema:
                                    type: string
                              requestBody:
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: '#/components/schemas/CreateCustomerCaseRequest'
                              responses:
                                '201':
                                  description: Sprawa zostala utworzona.
                                  content:
                                    application/json:
                                      schema:
                                        $ref: '#/components/schemas/CustomerCaseResponse'
                          /api/crm/customers/{customerId}/cases/{caseId}/notes:
                            post:
                              operationId: addCaseNote
                              responses:
                                '204':
                                  description: Notatka dodana.
                        components:
                          schemas:
                            CreateCustomerCaseRequest:
                              type: object
                              properties:
                                topic:
                                  type: string
                                priority:
                                  $ref: '#/components/schemas/CasePriority'
                            CasePriority:
                              type: string
                              enum: [LOW, NORMAL, HIGH]
                            CustomerCaseResponse:
                              type: object
                              properties:
                                caseId:
                                  type: string
                                status:
                                  type: string
                        """,
                false
        ));

        var response = service.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                "CRM",
                "crm-case-service",
                "main",
                "src/main/resources/openapi/customer-case-api.yaml",
                "POST",
                "/api/crm/customers/{id}/cases",
                true,
                2,
                20_000
        ));

        assertEquals(GitLabOpenApiEndpointSliceService.STATUS_OK, response.status());
        assertEquals("openapi", response.specType());
        assertEquals("3.0.1", response.specVersion());
        assertEquals("/api/crm/customers/{customerId}/cases", response.matchedPath());
        assertEquals("createCustomerCase", response.operationId());
        assertEquals("YAML", response.format());
        assertEquals("METHOD_PATH_TEMPLATE", response.matchedBy());
        assertTrue(response.operation().containsKey("requestBody"));
        assertTrue(response.referencedComponents().containsKey("#/components/schemas/CreateCustomerCaseRequest"));
        assertEquals("Utworzenie sprawy klienta", response.summary());
        assertTrue(response.content().contains("CreateCustomerCaseRequest"));
        assertTrue(response.content().contains("CustomerCaseResponse"));
        assertTrue(response.content().contains("CasePriority"));
        assertFalse(response.content().contains("addCaseNote"));
        assertFalse(response.truncated());
        assertEquals(List.of(), response.limitations());
    }

    @Test
    void shouldRejectNonYamlFileBeforeReadingRepository() {
        var response = service.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                "CRM",
                "crm-case-service",
                "main",
                "src/main/java/com/example/CustomerCaseController.java",
                "POST",
                "/api/crm/customers/{id}/cases",
                true,
                2,
                20_000
        ));

        assertEquals(GitLabOpenApiEndpointSliceService.STATUS_UNSUPPORTED_FILE_TYPE, response.status());
        assertTrue(response.limitations().contains("Requested file is not a JSON, YAML or YML file."));
    }

    @Test
    void shouldReadJsonOperationByOperationIdWithoutChunkingTheDocument() {
        when(repositoryPort.readFile(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                500_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                """
                        {
                          "openapi": "3.1.0",
                          "info": {"title": "CRM Customer API", "version": "1.0.0"},
                          "security": [{"oauth": ["customers:read"]}],
                          "paths": {
                            "/api/customers/{customerId}": {
                              "parameters": [{"name": "customerId", "in": "path", "required": true}],
                              "get": {
                                "operationId": "getCustomer",
                                "summary": "Get customer",
                                "responses": {
                                  "200": {
                                    "description": "Customer",
                                    "content": {
                                      "application/json": {
                                        "schema": {"$ref": "#/components/schemas/Customer"}
                                      }
                                    }
                                  },
                                  "400": {
                                    "description": "Invalid request",
                                    "content": {"application/json": {"schema": {"$ref": "errors.yaml#/components/schemas/Error"}}}
                                  }
                                }
                              }
                            },
                            "/api/customers": {
                              "post": {"operationId": "createCustomer", "responses": {"201": {"description": "Created"}}}
                            }
                          },
                          "components": {
                            "schemas": {
                              "Customer": {"type": "object", "properties": {"customerId": {"type": "string"}}}
                            }
                          }
                        }
                        """,
                false
        ));

        var response = service.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                null,
                null,
                "getCustomer",
                true,
                2,
                20_000
        ));

        assertEquals(GitLabOpenApiEndpointSliceService.STATUS_OK, response.status());
        assertEquals("JSON", response.format());
        assertEquals("OPERATION_ID", response.matchedBy());
        assertEquals("GET", response.httpMethod());
        assertEquals("/api/customers/{customerId}", response.matchedPath());
        assertEquals("getCustomer", response.operationId());
        assertTrue(response.effectiveContext().containsKey("security"));
        assertTrue(response.effectiveContext().containsKey("pathParameters"));
        assertTrue(response.referencedComponents().containsKey("#/components/schemas/Customer"));
        assertEquals(List.of("errors.yaml#/components/schemas/Error"), response.unresolvedReferences());
        assertFalse(response.content().contains("createCustomer"));
    }

    @Test
    void shouldReturnBoundedCandidatesForMissingOperationId() {
        when(repositoryPort.readFile(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                500_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                """
                        {
                          "swagger": "2.0",
                          "info": {"title": "CRM Customer API", "version": "1.0.0"},
                          "paths": {
                            "/customers/{customerId}": {
                              "get": {"operationId": "getCustomer", "responses": {"200": {"description": "OK"}}}
                            }
                          }
                        }
                        """,
                false
        ));

        var response = service.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                null,
                null,
                "findCustomer",
                true,
                2,
                20_000
        ));

        assertEquals(GitLabOpenApiEndpointSliceService.STATUS_ENDPOINT_NOT_FOUND, response.status());
        assertEquals(1, response.candidates().size());
        assertEquals("getCustomer", response.candidates().get(0).operationId());
    }

    @Test
    void shouldCompactLargeOperationWithoutReturningBrokenJsonSlice() {
        var longDescription = "x".repeat(5_000);
        when(repositoryPort.readFile(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                500_000
        )).thenReturn(new GitLabRepositoryFileContent(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                """
                        {
                          "openapi": "3.1.0",
                          "info": {"title": "CRM Customer API", "version": "1.0.0"},
                          "paths": {
                            "/customers": {
                              "get": {
                                "operationId": "listCustomers",
                                "description": "%s",
                                "responses": {"200": {"description": "OK", "examples": {"large": {"value": "%s"}}}}
                              }
                            }
                          }
                        }
                        """.formatted(longDescription, longDescription),
                false
        ));

        var response = service.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                "CRM",
                "crm-web",
                "pinned-commit",
                "contracts/customer-api.json",
                "GET",
                "/customers",
                null,
                true,
                2,
                1_000
        ));

        assertEquals(GitLabOpenApiEndpointSliceService.STATUS_OK, response.status());
        assertTrue(response.truncated());
        assertFalse(response.omittedSections().isEmpty());
        assertTrue(response.content().endsWith("```"));
        assertTrue(response.operation().containsKey("operationId"));
    }
}
