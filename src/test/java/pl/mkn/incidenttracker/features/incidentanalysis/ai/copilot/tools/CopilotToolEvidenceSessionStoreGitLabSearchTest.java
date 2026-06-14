package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

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
                          "group": "sample/runtime",
                          "branch": "main",
                          "repositories": [
                            {
                              "repositoryId": "orders-api-repo",
                              "name": "Orders API",
                              "summary": "Orders API repository.",
                              "projectName": "orders-api",
                              "gitLabPath": "sample/runtime/orders-api",
                              "aliases": ["orders-api"],
                              "repositoryType": "service",
                              "lifecycleStatus": "active",
                              "systems": ["orders"],
                              "boundedContexts": ["orders"],
                              "processes": ["order-process"],
                              "integrations": [],
                              "relatedRepositoryIds": [],
                              "packagePrefixes": ["pl.mkn.orders"],
                              "endpointPrefixes": ["/orders"],
                              "modulePaths": ["orders-api"]
                            }
                          ],
                          "codeSearchScopes": [
                            {
                              "scopeId": "orders-code-search",
                              "name": "Orders Code Search Scope",
                              "scopeType": "system",
                              "lifecycleStatus": "active",
                              "target": {"type": "system", "id": "orders"},
                              "useFor": ["code-search", "incident-analysis"],
                              "repositories": [
                                {
                                  "repositoryId": "orders-api-repo",
                                  "role": "primary-implementation",
                                  "priority": 1,
                                  "projectNames": ["orders-api"],
                                  "moduleIds": ["orders-api"],
                                  "reason": "Main orders API repository.",
                                  "readFor": ["runtime-entrypoints"]
                                }
                              ],
                              "projectNames": ["orders-api"],
                              "packagePrefixes": ["pl.mkn.orders"],
                              "classHints": ["OrderService"],
                              "endpointHints": ["/orders"],
                              "queueTopicHints": [],
                              "traversal": {
                                "rules": ["Read orders-api-repo first."],
                                "expandWhen": []
                              }
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
                              "group": "sample/runtime",
                              "projectName": "orders-api",
                              "branch": "main",
                              "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
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
                          "group": "sample/runtime",
                          "projectName": "orders-api",
                          "branch": "main",
                          "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
                          "packageName": "pl.mkn.orders",
                          "imports": ["java.time.Duration"],
                          "classes": ["OrderService"],
                          "annotations": ["Service"],
                          "methodSignatures": ["void handle(Order order)"],
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
                          "group": "sample/runtime",
                          "branch": "main",
                          "groups": [
                            {
                              "role": "repository",
                              "candidates": [
                                {
                                  "group": "sample/runtime",
                                  "projectName": "orders-api",
                                  "branch": "main",
                                  "filePath": "src/main/java/pl/mkn/orders/OrderRepository.java",
                                  "matchReason": "repository keyword",
                                  "matchScore": 31,
                                  "inferredRole": "repository",
                                  "recommendedReadStrategy": "chunk",
                                  "preview": "interface OrderRepository"
                                }
                              ]
                            }
                          ],
                          "recommendedNextReads": ["orders-api:src/main/java/pl/mkn/orders/OrderRepository.java"]
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
                          "group": "sample/runtime",
                          "projectName": "orders-api",
                          "branch": "main",
                          "sourcePathPrefix": "src/main/java",
                          "endpoint": {
                            "endpointId": "GET /api/orders/{orderId} -> pl.mkn.orders.OrderController#getOrder",
                            "httpMethods": ["GET"],
                            "path": "/api/orders/{orderId}",
                            "pathExpression": "/api/orders/{orderId}",
                            "controllerClass": "pl.mkn.orders.OrderController",
                            "handlerMethod": "getOrder",
                            "filePath": "src/main/java/pl/mkn/orders/OrderController.java",
                            "lineStart": 12,
                            "lineEnd": 20,
                            "requestTypes": ["@PathVariable String orderId"],
                            "responseTypes": ["OrderResponse"],
                            "annotations": ["RestController", "GetMapping"],
                            "confidence": "HIGH",
                            "limitations": [],
                            "suggestedNextReads": []
                          },
                          "files": [
                            {
                              "path": "src/main/java/pl/mkn/orders/OrderController.java",
                              "role": "CONTROLLER",
                              "priority": 1,
                              "symbols": ["getOrder"],
                              "reason": "Endpoint handler and local controller flow.",
                              "confidence": "HIGH"
                            },
                            {
                              "path": "src/main/java/pl/mkn/orders/OrderService.java",
                              "role": "USE_CASE_SERVICE",
                              "priority": 2,
                              "symbols": ["findOrder"],
                              "reason": "Direct service dependency.",
                              "confidence": "MEDIUM"
                            }
                          ],
                          "relations": [
                            {
                              "from": "pl.mkn.orders.OrderController#getOrder",
                              "to": "pl.mkn.orders.OrderService#findOrder",
                              "kind": "LOCAL_METHOD_CALL",
                              "confidence": "MEDIUM",
                              "reason": "Controller calls service."
                            }
                          ],
                          "unresolved": [
                            {
                              "symbol": "OrderMapper",
                              "ownerPath": "src/main/java/pl/mkn/orders/OrderService.java",
                              "reason": "Implementation not found in narrowed traversal.",
                              "searchedKeywords": ["OrderMapper"],
                              "candidates": []
                            }
                          ],
                          "limitations": ["Traversal stopped at max depth."],
                          "suggestedNextReads": [
                            "orders-api:src/main/java/pl/mkn/orders/OrderController.java via gitlab_read_repository_file_outline",
                            "orders-api:src/main/java/pl/mkn/orders/OrderService.java via gitlab_read_repository_file_outline"
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
                          "group": "sample/runtime",
                          "branch": "main",
                          "searchedClass": "OrderEntity",
                          "searchKeywords": ["OrderEntity", "ORDER"],
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
        assertTrue(listAttributes.get("repositories").contains("orders-api"));
        assertTrue(listAttributes.get("codeSearchScopes").contains("orders-code-search"));

        var searchAttributes = attributes(lastSection.items().get(1));
        assertEquals("gitlab_search_repository_candidates", searchAttributes.get("toolName"));
        assertEquals("Szukam kandydatow plikow.", searchAttributes.get("reason"));
        assertEquals("1", searchAttributes.get("candidateCount"));
        assertEquals("2", searchAttributes.get("toolCaptureOrder"));
        assertTrue(searchAttributes.get("candidates").contains("OrderService.java"));

        var outlineAttributes = attributes(lastSection.items().get(2));
        assertEquals("gitlab_read_repository_file_outline", outlineAttributes.get("toolName"));
        assertTrue(outlineAttributes.get("classes").contains("OrderService"));
        assertEquals("3", outlineAttributes.get("toolCaptureOrder"));

        var flowAttributes = attributes(lastSection.items().get(3));
        assertEquals("gitlab_find_flow_context", flowAttributes.get("toolName"));
        assertEquals("1", flowAttributes.get("groupCount"));
        assertEquals("1", flowAttributes.get("recommendedNextReadCount"));
        assertTrue(flowAttributes.get("groups").contains("OrderRepository.java"));
        assertEquals("4", flowAttributes.get("toolCaptureOrder"));

        var endpointContextAttributes = attributes(lastSection.items().get(4));
        assertEquals("gitlab_build_endpoint_use_case_context", endpointContextAttributes.get("toolName"));
        assertEquals("Buduje liste plikow dla endpointu.", endpointContextAttributes.get("reason"));
        assertEquals("orders-api", endpointContextAttributes.get("projectName"));
        assertEquals("/api/orders/{orderId}", endpointContextAttributes.get("endpointPath"));
        assertEquals("getOrder", endpointContextAttributes.get("handlerMethod"));
        assertEquals("2", endpointContextAttributes.get("fileCount"));
        assertEquals("1", endpointContextAttributes.get("relationCount"));
        assertEquals("1", endpointContextAttributes.get("unresolvedCount"));
        assertEquals("2", endpointContextAttributes.get("suggestedNextReadCount"));
        assertEquals("MEDIUM", endpointContextAttributes.get("confidence"));
        assertEquals("5", endpointContextAttributes.get("toolCaptureOrder"));
        assertTrue(endpointContextAttributes.get("files").contains("OrderController.java"));
        assertTrue(endpointContextAttributes.get("files").contains("OrderService.java"));
        assertTrue(endpointContextAttributes.get("relations").contains("LOCAL_METHOD_CALL"));
        assertTrue(endpointContextAttributes.get("unresolved").contains("OrderMapper"));
        assertTrue(endpointContextAttributes.get("suggestedNextReads").contains("gitlab_read_repository_file_outline"));

        var classAttributes = attributes(lastSection.items().get(5));
        assertEquals("gitlab_find_class_references", classAttributes.get("toolName"));
        assertEquals("OrderEntity", classAttributes.get("searchedClass"));
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
                new CopilotToolSessionContext("run-1", sessionId, "corr-123", "zt01", "main", "sample/runtime"),
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
