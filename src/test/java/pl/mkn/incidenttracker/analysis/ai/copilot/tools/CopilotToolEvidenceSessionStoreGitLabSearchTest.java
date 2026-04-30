package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotToolEvidenceSessionStoreGitLabSearchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeGitLabDiscoveryCallsAsUserEvidence() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var toolEvidenceListener = gitLabToolEvidenceListener(registry);
        var capturedSections = new ArrayList<AnalysisEvidenceSection>();
        registry.registerSession("session-1", listener(capturedSections));

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

        assertEquals(4, capturedSections.size());
        var lastSection = capturedSections.get(3);
        assertEquals("gitlab", lastSection.provider());
        assertEquals("tool-discovery", lastSection.category());
        assertEquals(4, lastSection.items().size());

        var searchAttributes = attributes(lastSection.items().get(0));
        assertEquals("gitlab_search_repository_candidates", searchAttributes.get("toolName"));
        assertEquals("Szukam kandydatow plikow.", searchAttributes.get("reason"));
        assertEquals("1", searchAttributes.get("candidateCount"));
        assertEquals("1", searchAttributes.get("toolCaptureOrder"));
        assertTrue(searchAttributes.get("candidates").contains("OrderService.java"));

        var outlineAttributes = attributes(lastSection.items().get(1));
        assertEquals("gitlab_read_repository_file_outline", outlineAttributes.get("toolName"));
        assertTrue(outlineAttributes.get("classes").contains("OrderService"));
        assertEquals("2", outlineAttributes.get("toolCaptureOrder"));

        var flowAttributes = attributes(lastSection.items().get(2));
        assertEquals("gitlab_find_flow_context", flowAttributes.get("toolName"));
        assertEquals("1", flowAttributes.get("groupCount"));
        assertEquals("1", flowAttributes.get("recommendedNextReadCount"));
        assertTrue(flowAttributes.get("groups").contains("OrderRepository.java"));
        assertEquals("3", flowAttributes.get("toolCaptureOrder"));

        var classAttributes = attributes(lastSection.items().get(3));
        assertEquals("gitlab_find_class_references", classAttributes.get("toolName"));
        assertEquals("OrderEntity", classAttributes.get("searchedClass"));
        assertEquals("4", classAttributes.get("toolCaptureOrder"));
    }

    private AnalysisAiToolEvidenceListener listener(ArrayList<AnalysisEvidenceSection> capturedSections) {
        return new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSections.add(section);
            }
        };
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
