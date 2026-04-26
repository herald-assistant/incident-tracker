package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolEvidenceCaptureRegistryGitLabSearchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCaptureGitLabSearchResultsAsAuditEvidence() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", listener(capturedSection));

        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-search-1",
                "gitlab_search_repository_candidates",
                """
                        {
                          "projectNames": ["orders-api"],
                          "keywords": ["OrderService", "EntityNotFoundException"]
                        }
                        """,
                """
                        {
                          "candidates": [
                            {
                              "group": "sample/runtime",
                              "projectName": "orders-api",
                              "branch": "main",
                              "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
                              "matchReason": "Matched OrderService from stacktrace",
                              "matchScore": 120
                            }
                          ]
                        }
                        """
        );

        assertNotNull(capturedSection.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-search-results", capturedSection.get().category());

        var attributes = attributes(capturedSection.get());
        assertEquals("gitlab_search_repository_candidates", attributes.get("toolName"));
        assertTrue(attributes.get("seed").contains("orders-api"));
        assertEquals("orders-api", attributes.get("projectCandidates"));
        assertTrue(attributes.get("fileCandidates").contains("OrderService.java"));
        assertEquals("Matched OrderService from stacktrace", attributes.get("selectedReason"));
        assertEquals("candidateCount=1", attributes.get("resultSummary"));
    }

    @Test
    void shouldCaptureGitLabFlowContextAsAuditEvidence() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", listener(capturedSection));

        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-flow-1",
                "gitlab_find_flow_context",
                """
                        {
                          "projectNames": ["orders-api"],
                          "seedClass": "pl.mkn.orders.OrderService",
                          "seedMethod": "loadOrder"
                        }
                        """,
                """
                        {
                          "group": "sample/runtime",
                          "branch": "main",
                          "groups": [
                            {
                              "role": "service-or-orchestrator",
                              "candidates": [
                                {
                                  "group": "sample/runtime",
                                  "projectName": "orders-api",
                                  "branch": "main",
                                  "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
                                  "matchReason": "Seed class",
                                  "matchScore": 130,
                                  "inferredRole": "service-or-orchestrator",
                                  "recommendedReadStrategy": "outline-then-focused-chunk",
                                  "preview": "Seed class"
                                }
                              ]
                            }
                          ],
                          "recommendedNextReads": [
                            "orders-api:src/main/java/pl/mkn/orders/OrderService.java (service-or-orchestrator, outline-then-focused-chunk)"
                          ]
                        }
                        """
        );

        assertNotNull(capturedSection.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-flow-context", capturedSection.get().category());

        var attributes = attributes(capturedSection.get());
        assertEquals("gitlab_find_flow_context", attributes.get("toolName"));
        assertTrue(attributes.get("seed").contains("pl.mkn.orders.OrderService"));
        assertEquals("orders-api", attributes.get("projectCandidates"));
        assertTrue(attributes.get("fileCandidates").contains("OrderService.java"));
        assertTrue(attributes.get("selectedReason").contains("service-or-orchestrator=1"));
        assertEquals("groupCount=1 candidateCount=1 recommendedNextReadsCount=1", attributes.get("resultSummary"));
    }

    @Test
    void shouldCaptureGitLabOutlineAsAuditEvidence() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", listener(capturedSection));

        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-outline-1",
                "gitlab_read_repository_file_outline",
                """
                        {
                          "projectName": "orders-api",
                          "filePath": "src/main/java/pl/mkn/orders/OrderService.java"
                        }
                        """,
                """
                        {
                          "group": "sample/runtime",
                          "projectName": "orders-api",
                          "branch": "main",
                          "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
                          "packageName": "pl.mkn.orders",
                          "imports": ["import pl.mkn.orders.OrderRepository;"],
                          "classes": ["public class OrderService"],
                          "annotations": ["@Service"],
                          "methodSignatures": ["public Order loadOrder(String id)"],
                          "inferredRole": "service-or-orchestrator",
                          "truncated": false
                        }
                        """
        );

        assertNotNull(capturedSection.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-flow-context", capturedSection.get().category());

        var attributes = attributes(capturedSection.get());
        assertEquals("gitlab_read_repository_file_outline", attributes.get("toolName"));
        assertTrue(attributes.get("fileCandidates").contains("OrderService.java"));
        assertTrue(attributes.get("selectedReason").contains("inferredRole=service-or-orchestrator"));
        assertEquals(
                "package=pl.mkn.orders classCount=1 methodSignatureCount=1 truncated=false",
                attributes.get("resultSummary")
        );
    }

    private AnalysisAiToolEvidenceListener listener(AtomicReference<AnalysisEvidenceSection> capturedSection) {
        return new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        };
    }

    private Map<String, String> attributes(AnalysisEvidenceSection section) {
        return section.items().get(0).attributes().stream()
                .collect(Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value()
                ));
    }
}
