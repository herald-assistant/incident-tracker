package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotToolEvidenceCaptureRegistryDatabaseSummaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPublishDatabaseResultWithModelProvidedReason() {
        var registry = toolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        });

        registry.captureToolResult(
                "session-1",
                "tool-call-db-1",
                "db_count_rows",
                """
                        {
                          "table": {"schema": "CLP", "tableName": "ORDER_EVENT"},
                          "filters": [
                            {"column": "correlation_id", "operator": "EQ", "values": ["corr-123"]}
                          ],
                          "reason": "Sprawdzam, czy istnieja rekordy dla correlationId."
                        }
                        """,
                """
                        {
                          "environment": "zt002",
                          "databaseAlias": "oracle",
                          "table": {"schema": "CLP", "tableName": "ORDER_EVENT"},
                          "count": 3,
                          "appliedFilters": ["CORRELATION_ID = corr-123"],
                          "warnings": []
                        }
                        """
        );

        assertNotNull(capturedSection.get());
        assertEquals("database", capturedSection.get().provider());
        assertEquals("tool-results", capturedSection.get().category());
        assertEquals(1, capturedSection.get().items().size());

        var attributes = attributes(capturedSection.get());
        assertEquals("Sprawdzam, czy istnieja rekordy dla correlationId.", attributes.get("reason"));
        assertTrue(attributes.get("result").contains("\"count\" : 3"));
        assertTrue(attributes.get("result").contains("\"tableName\" : \"ORDER_EVENT\""));
    }

    @Test
    void shouldAttachMonotonicCaptureOrderAcrossToolEvidenceGroups() {
        var registry = toolEvidenceCaptureRegistry(objectMapper);
        var capturedSections = new ArrayList<AnalysisEvidenceSection>();
        registry.registerSession("session-1", new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSections.add(section);
            }
        });

        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-1",
                "gitlab_read_repository_file",
                "{\"reason\":\"Czytam plik.\"}",
                """
                        {
                          "group": "sample/runtime",
                          "projectName": "orders-api",
                          "branch": "main",
                          "filePath": "src/main/java/pl/mkn/orders/OrderService.java",
                          "content": "class OrderService {}",
                          "truncated": false
                        }
                        """
        );
        registry.captureToolResult(
                "session-1",
                "tool-call-db-1",
                "db_count_rows",
                "{\"reason\":\"Sprawdzam rekordy.\"}",
                "{\"count\":3}"
        );

        assertEquals(2, capturedSections.size());
        assertEquals("gitlab", capturedSections.get(0).provider());
        assertEquals("1", attributes(capturedSections.get(0)).get("toolCaptureOrder"));
        assertEquals("database", capturedSections.get(1).provider());
        assertEquals("2", attributes(capturedSections.get(1)).get("toolCaptureOrder"));
    }

    private Map<String, String> attributes(AnalysisEvidenceSection section) {
        return section.items().get(0).attributes().stream()
                .collect(Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value()
                ));
    }
}
