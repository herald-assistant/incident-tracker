package pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.tdw.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotToolEvidenceSessionStoreGitLabSearchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeGitLabDiscoveryCallsAsUserEvidence() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var toolEvidenceListener = gitLabToolEvidenceListener(registry);
        var capturedSections = new ArrayList<AnalysisEvidenceSection>();
        registry.registerSession("session-1", capturedSections::add);

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-list-1",
                "gitlab_list_available_repositories",
                "{\"reason\":\"Szukam dostepnych repozytoriow.\"}",
                """
                        {
                          "group": "CRM/runtime",
                          "branch": "main",
                          "repositories": [
                            {
                              "repositoryId": "crm-customer-api-repo",
                              "name": "CRM Customers API",
                              "summary": "CRM customer API repository.",
                              "projectName": "crm-customer-api",
                              "gitLabPath": "CRM/runtime/crm-customer-api",
                              "aliases": ["crm-customer-api"],
                              "repositoryType": "service",
                              "lifecycleStatus": "active",
                              "systems": ["crm"],
                              "boundedContexts": ["customer"],
                              "processes": ["customer-process"],
                              "integrations": [],
                              "relatedRepositoryIds": []
                            }
                          ],
                          "codeSearchScopes": [
                            {
                              "scopeId": "crm-code-search",
                              "name": "CRM Code Search Scope",
                              "scopeType": "system",
                              "lifecycleStatus": "active",
                              "target": {"type": "system", "id": "crm"},
                              "useFor": ["code-search", "incident-analysis"],
                              "repositories": [
                                {
                                  "repositoryId": "crm-customer-api-repo",
                                  "role": "primary",
                                  "priority": 1,
                                  "projectNames": ["crm-customer-api"],
                                  "reason": "Main customers API repository.",
                                  "readFor": ["runtime-entrypoints"]
                                }
                              ],
                              "projectNames": ["crm-customer-api"],
                              "limitations": ["Generated clients are partial."]
                            }
                          ]
                        }
                        """
        );
        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-search-1",
                "gitlab_search_repository_candidates",
                "{\"reason\":\"Szukam kandydatow plikow.\"}",
                """
                        {
                          "candidates": [
                            {
                              "group": "CRM/runtime",
                              "projectName": "crm-customer-api",
                              "branch": "main",
                              "filePath": "src/main/java/com/example/crm/customer/CustomerService.java",
                              "matchReason": "service keyword",
                              "matchScore": 42
                            }
                          ]
                        }
                        """
        );
        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-outline-1",
                "gitlab_read_repository_file_outline",
                "{\"reason\":\"Sprawdzam zarys pliku.\"}",
                """
                        {
                          "group": "CRM/runtime",
                          "projectName": "crm-customer-api",
                          "branch": "main",
                          "filePath": "src/main/java/com/example/crm/customer/CustomerService.java",
                          "packageName": "com.example.crm.customer",
                          "imports": ["java.time.Duration"],
                          "typeSummaries": [
                            {
                              "name": "CustomerService",
                              "qualifiedName": "com.example.crm.customer.CustomerService",
                              "kind": "class",
                              "declaration": "public class CustomerService {",
                              "lineStart": 10,
                              "lineEnd": 42,
                              "modifiers": ["public"],
                              "annotations": ["@Service"]
                            }
                          ],
                          "fieldSummaries": [
                            {
                              "declaringTypeName": "com.example.crm.customer.CustomerService",
                              "name": "customerRepository",
                              "type": "CustomerRepository",
                              "lineStart": 12,
                              "lineEnd": 12,
                              "modifiers": ["private", "final"],
                              "annotations": []
                            }
                          ],
                          "constructorSummaries": [
                            {
                              "declaringTypeName": "com.example.crm.customer.CustomerService",
                              "signature": "public CustomerService(CustomerRepository customerRepository)",
                              "lineStart": 14,
                              "lineEnd": 16,
                              "modifiers": ["public"],
                              "annotations": ["@Autowired"]
                            }
                          ],
                          "methodSummaries": [
                            {
                              "declaringTypeName": "com.example.crm.customer.CustomerService",
                              "signature": "void handle(Customer customer)",
                              "lineStart": 18,
                              "lineEnd": 24,
                              "modifiers": [],
                              "annotations": ["@Transactional"]
                            }
                          ],
                          "inferredRole": "service-or-orchestrator",
                          "truncated": false
                        }
                        """
        );
        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-flow-1",
                "gitlab_find_flow_context",
                "{\"reason\":\"Szukam kontekstu przeplywu.\"}",
                """
                        {
                          "group": "CRM/runtime",
                          "branch": "main",
                          "groups": [
                            {
                              "role": "repository",
                              "candidates": [
                                {
                                  "group": "CRM/runtime",
                                  "projectName": "crm-customer-api",
                                  "branch": "main",
                                  "filePath": "src/main/java/com/example/crm/customer/CustomerProfileRepository.java",
                                  "matchReason": "repository keyword",
                                  "matchScore": 31,
                                  "inferredRole": "repository",
                                  "recommendedReadStrategy": "chunk",
                                  "preview": "interface CustomerRepository"
                                }
                              ]
                            }
                          ],
                          "recommendedNextReads": ["crm-customer-api:src/main/java/com/example/crm/customer/CustomerProfileRepository.java"]
                        }
                        """
        );
        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-endpoint-context-1",
                "gitlab_build_endpoint_use_case_context",
                "{\"reason\":\"Buduje liste plikow dla endpointu.\"}",
                """
                        {
                          "group": "CRM/runtime",
                          "projectName": "crm-customer-api",
                          "branch": "main",
                          "endpoint": {
                            "endpointId": "GET /api/customers/{customerId} -> com.example.crm.customer.CustomerController#getCustomer",
                            "httpMethods": ["GET"],
                            "path": "/api/customers/{customerId}",
                            "pathExpression": "/api/customers/{customerId}",
                            "controllerClass": "com.example.crm.customer.CustomerController",
                            "handlerMethod": "getCustomer",
                            "filePath": "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                            "lineStart": 12,
                            "lineEnd": 20,
                            "requestTypes": ["@PathVariable String customerId"],
                            "responseTypes": ["CustomerProfileResponse"],
                            "annotations": ["RestController", "GetMapping"],
                            "confidence": "HIGH",
                            "limitations": [],
                            "suggestedNextReads": []
                          },
                          "files": [
                            {
                              "path": "src/main/java/com/example/crm/customer/CustomerProfileController.java",
                              "role": "CONTROLLER",
                              "priority": 1,
                              "symbols": ["getCustomer"],
                              "reason": "Endpoint handler and local controller flow.",
                              "confidence": "HIGH"
                            },
                            {
                              "path": "src/main/java/com/example/crm/customer/CustomerService.java",
                              "role": "USE_CASE_SERVICE",
                              "priority": 2,
                              "symbols": ["findCustomer"],
                              "reason": "Direct service dependency.",
                              "confidence": "MEDIUM"
                            }
                          ],
                          "relations": [
                            {
                              "from": "com.example.crm.customer.CustomerController#getCustomer",
                              "to": "com.example.crm.customer.CustomerService#findCustomer",
                              "kind": "LOCAL_METHOD_CALL",
                              "confidence": "MEDIUM",
                              "reason": "Controller calls service."
                            }
                          ],
                          "unresolved": [
                            {
                              "symbol": "CustomerMapper",
                              "ownerPath": "src/main/java/com/example/crm/customer/CustomerService.java",
                              "reason": "Implementation not found in narrowed traversal.",
                              "searchedKeywords": ["CustomerMapper"],
                              "candidates": []
                            }
                          ],
                          "limitations": ["Traversal stopped at max depth."],
                          "suggestedNextReads": [
                            "crm-customer-api:src/main/java/com/example/crm/customer/CustomerProfileController.java via gitlab_read_repository_file_outline",
                            "crm-customer-api:src/main/java/com/example/crm/customer/CustomerService.java via gitlab_read_repository_file_outline"
                          ],
                          "limits": {
                            "maxDepth": 5,
                            "maxFiles": 25,
                            "maxReadFiles": 60,
                            "maxDepthReached": false,
                            "maxFilesReached": false,
                            "readFileCount": 4,
                            "readFileLimitReached": false
                          },
                          "confidence": "MEDIUM"
                        }
                        """
        );
        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-gitlab-class-1",
                "gitlab_find_class_references",
                "{\"reason\":\"Szukam referencji klasy.\"}",
                """
                        {
                          "group": "CRM/runtime",
                          "branch": "main",
                          "searchedClass": "CustomerEntity",
                          "searchKeywords": ["CustomerEntity", "ORDER"],
                          "groups": [],
                          "recommendedNextReads": []
                        }
                        """
        );

        assertEquals(6, capturedSections.size());
        var lastSection = capturedSections.get(5);
        assertEquals("gitlab", lastSection.provider());
        assertEquals("tool-discovery", lastSection.category());
        assertEquals(6, lastSection.items().size());

        var listAttributes = attributes(lastSection.items().get(0));
        assertEquals("gitlab_list_available_repositories", listAttributes.get("toolName"));
        assertEquals("Szukam dostepnych repozytoriow.", listAttributes.get("reason"));
        assertEquals("1", listAttributes.get("repositoryCount"));
        assertEquals("1", listAttributes.get("codeSearchScopeCount"));
        assertEquals("1", listAttributes.get("toolCaptureOrder"));
        assertTrue(listAttributes.get("repositories").contains("crm-customer-api"));
        assertTrue(listAttributes.get("codeSearchScopes").contains("crm-code-search"));

        var searchAttributes = attributes(lastSection.items().get(1));
        assertEquals("gitlab_search_repository_candidates", searchAttributes.get("toolName"));
        assertEquals("Szukam kandydatow plikow.", searchAttributes.get("reason"));
        assertEquals("1", searchAttributes.get("candidateCount"));
        assertEquals("2", searchAttributes.get("toolCaptureOrder"));
        assertTrue(searchAttributes.get("candidates").contains("CustomerService.java"));

        var outlineAttributes = attributes(lastSection.items().get(2));
        assertEquals("gitlab_read_repository_file_outline", outlineAttributes.get("toolName"));
        assertEquals("1", outlineAttributes.get("typeSummaryCount"));
        assertEquals("1", outlineAttributes.get("fieldSummaryCount"));
        assertEquals("1", outlineAttributes.get("constructorSummaryCount"));
        assertEquals("1", outlineAttributes.get("methodSummaryCount"));
        assertTrue(outlineAttributes.get("typeSummaries").contains("CustomerService"));
        assertTrue(outlineAttributes.get("typeSummaries").contains("@Service"));
        assertTrue(outlineAttributes.get("fieldSummaries").contains("customerRepository"));
        assertTrue(outlineAttributes.get("constructorSummaries").contains("@Autowired"));
        assertTrue(outlineAttributes.get("methodSummaries").contains("@Transactional"));
        assertEquals("3", outlineAttributes.get("toolCaptureOrder"));

        var flowAttributes = attributes(lastSection.items().get(3));
        assertEquals("gitlab_find_flow_context", flowAttributes.get("toolName"));
        assertEquals("1", flowAttributes.get("groupCount"));
        assertEquals("1", flowAttributes.get("recommendedNextReadCount"));
        assertTrue(flowAttributes.get("groups").contains("CustomerProfileRepository.java"));
        assertEquals("4", flowAttributes.get("toolCaptureOrder"));

        var endpointContextAttributes = attributes(lastSection.items().get(4));
        assertEquals("gitlab_build_endpoint_use_case_context", endpointContextAttributes.get("toolName"));
        assertEquals("Buduje liste plikow dla endpointu.", endpointContextAttributes.get("reason"));
        assertEquals("crm-customer-api", endpointContextAttributes.get("projectName"));
        assertEquals("/api/customers/{customerId}", endpointContextAttributes.get("endpointPath"));
        assertEquals("getCustomer", endpointContextAttributes.get("handlerMethod"));
        assertEquals("2", endpointContextAttributes.get("fileCount"));
        assertEquals("1", endpointContextAttributes.get("relationCount"));
        assertEquals("1", endpointContextAttributes.get("unresolvedCount"));
        assertEquals("2", endpointContextAttributes.get("suggestedNextReadCount"));
        assertEquals("MEDIUM", endpointContextAttributes.get("confidence"));
        assertEquals("5", endpointContextAttributes.get("toolCaptureOrder"));
        assertTrue(endpointContextAttributes.get("files").contains("CustomerProfileController.java"));
        assertTrue(endpointContextAttributes.get("files").contains("CustomerService.java"));
        assertTrue(endpointContextAttributes.get("relations").contains("LOCAL_METHOD_CALL"));
        assertTrue(endpointContextAttributes.get("unresolved").contains("CustomerMapper"));
        assertTrue(endpointContextAttributes.get("suggestedNextReads").contains("gitlab_read_repository_file_outline"));

        var classAttributes = attributes(lastSection.items().get(5));
        assertEquals("gitlab_find_class_references", classAttributes.get("toolName"));
        assertEquals("CustomerEntity", classAttributes.get("searchedClass"));
        assertEquals("6", classAttributes.get("toolCaptureOrder"));
    }

    private Map<String, String> attributes(AnalysisEvidenceItem item) {
        assertNotNull(item);
        return item.attributes().stream()
                .collect(Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value()
                ));
    }

    private void capture(
            GitLabToolEvidenceCaptureListener listener,
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        listener.onToolInvocationFinished(new CopilotToolInvocationFinishedEvent(
                new CopilotToolSessionContext("run-1", sessionId, "corr-123", "zt01", "main", "CRM/runtime"),
                sessionId,
                toolCallId,
                toolName,
                rawArguments,
                CopilotToolInvocationOutcome.COMPLETED,
                rawResult,
                1L,
                null
        ));
    }

    private GitLabToolEvidenceCaptureListener gitLabToolEvidenceListener(
            CopilotToolEvidenceSessionStore registry
    ) {
        var payloadReader = new JsonPayloadReader(objectMapper);
        return new GitLabToolEvidenceCaptureListener(
                registry,
                new GitLabToolEvidenceMapper(objectMapper, payloadReader)
        );
    }
}
