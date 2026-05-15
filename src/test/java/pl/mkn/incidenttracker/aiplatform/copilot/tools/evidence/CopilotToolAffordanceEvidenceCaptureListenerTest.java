package pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolAffordanceEvidenceCaptureListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCaptureCompactAffordanceEvidenceWithoutRawToolPayload() throws JsonProcessingException {
        var captured = new ArrayList<AnalysisEvidenceSection>();
        var store = new CopilotToolEvidenceSessionStore();
        store.registerSession("sdk-session-1", captured::add);
        var listener = new CopilotToolAffordanceEvidenceCaptureListener(
                store,
                new CopilotToolAffordanceEvidenceMapper(objectMapper)
        );

        listener.onToolInvocationFinished(finished(
                "call-1",
                "opctx_get_entity",
                """
                        {
                          "type": "codeSearchScope",
                          "id": "clp-collateral-full-scope",
                          "reason": "need focused code search"
                        }
                        """,
                """
                        {
                          "type": "codeSearchScope",
                          "id": "clp-collateral-full-scope",
                          "label": "CLP collateral full scope",
                          "codeSearch": {
                            "payload": "VERY_LARGE_PAYLOAD"
                          },
                          "sourceRefs": ["src/main/resources/operational-context/repo-map.yml"],
                          "affordances": {
                            "profile": "default",
                            "availableExpansions": ["include=codeSearch", "include=relations"],
                            "suggestedNextReads": [
                              "opctx_get_entity(type=codeSearchScope,id=clp-collateral-full-scope,include=[codeSearch])"
                            ],
                            "suggestedTools": ["opctx_search"],
                            "reasonToExpand": "Need full repository hint inventory before GitLab search.",
                            "omittedBecause": ["full codeSearch hints are expanded-only"],
                            "limitations": ["default omits low-signal source refs"],
                            "links": [
                              {
                                "rel": "expanded",
                                "href": "/api/operational-context/entities/codeSearchScope/clp-collateral-full-scope?profile=expanded"
                              }
                            ],
                            "truncation": {
                              "truncated": true,
                              "reason": "default profile compacted heavy code-search hints",
                              "returnedCounts": {"hints": 12},
                              "omittedCounts": {"hints": 79}
                            }
                          }
                        }
                        """
        ));

        assertEquals(1, captured.size());
        var section = captured.get(0);
        assertEquals(CopilotToolAffordanceEvidenceMapper.PROVIDER, section.provider());
        assertEquals(CopilotToolAffordanceEvidenceMapper.CATEGORY, section.category());
        assertEquals(1, section.items().size());

        var item = section.items().get(0);
        assertEquals("Tool affordances: opctx_get_entity", item.title());

        var attributes = item.attributes().stream()
                .collect(Collectors.toMap(
                        AnalysisEvidenceAttribute::name,
                        AnalysisEvidenceAttribute::value,
                        (first, ignored) -> first
                ));
        assertEquals("opctx_get_entity", attributes.get("toolName"));
        assertEquals("call-1", attributes.get("toolCallId"));
        assertEquals("default", attributes.get("profile"));
        assertEquals("true", attributes.get("truncationTruncated"));
        assertTrue(attributes.get("suggestedNextReads").contains("include=[codeSearch]"));
        assertTrue(attributes.get("truncationReason").contains("compacted heavy code-search hints"));
        assertTrue(attributes.get("truncationOmittedCounts").contains("\"hints\":79"));
        assertTrue(attributes.containsKey("toolCaptureOrder"));

        var serializedSection = objectMapper.writeValueAsString(section);
        assertFalse(serializedSection.contains("VERY_LARGE_PAYLOAD"));
        assertFalse(serializedSection.contains("\"payload\""));
        assertFalse(serializedSection.contains("\"codeSearch\""));
    }

    @Test
    void shouldIgnoreToolResultsWithoutAffordances() {
        var captured = new ArrayList<AnalysisEvidenceSection>();
        var store = new CopilotToolEvidenceSessionStore();
        store.registerSession("sdk-session-1", captured::add);
        var listener = new CopilotToolAffordanceEvidenceCaptureListener(
                store,
                new CopilotToolAffordanceEvidenceMapper(objectMapper)
        );

        listener.onToolInvocationFinished(finished(
                "call-1",
                "opctx_search",
                "{}",
                "{\"status\":\"ok\",\"results\":[{\"id\":\"system/clp-limit-process\"}]}"
        ));

        assertTrue(captured.isEmpty());
    }

    private CopilotToolInvocationFinishedEvent finished(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        return new CopilotToolInvocationFinishedEvent(
                new CopilotToolSessionContext(
                        "run-1",
                        "logical-session-1",
                        "corr-123",
                        "dev3",
                        "main",
                        "sample/runtime"
                ),
                "sdk-session-1",
                toolCallId,
                toolName,
                rawArguments,
                CopilotToolInvocationOutcome.COMPLETED,
                rawResult,
                12L,
                null
        );
    }
}
