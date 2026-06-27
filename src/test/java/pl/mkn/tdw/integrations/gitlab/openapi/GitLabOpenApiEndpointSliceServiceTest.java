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
        assertTrue(response.limitations().contains("Requested file is not a YAML file."));
    }
}
