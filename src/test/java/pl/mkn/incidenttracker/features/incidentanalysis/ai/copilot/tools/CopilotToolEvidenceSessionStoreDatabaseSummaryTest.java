package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceMapper;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotToolEvidenceSessionStoreDatabaseSummaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPublishDatabaseResultWithModelProvidedReason() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var databaseToolEvidenceListener = databaseToolEvidenceListener(registry);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", capturedSection::set);

        capture(
                databaseToolEvidenceListener,
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
        var registry = toolEvidenceSessionStore(objectMapper);
        var gitLabToolEvidenceListener = gitLabToolEvidenceListener(registry);
        var databaseToolEvidenceListener = databaseToolEvidenceListener(registry);
        var capturedSections = new ArrayList<AnalysisEvidenceSection>();
        registry.registerSession("session-1", capturedSections::add);

        capture(
                gitLabToolEvidenceListener,
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
        capture(
                databaseToolEvidenceListener,
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

    private void capture(
            DatabaseToolEvidenceCaptureListener listener,
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        listener.onToolInvocationFinished(toolCompleted(sessionId, toolCallId, toolName, rawArguments, rawResult));
    }

    private void capture(
            GitLabToolEvidenceCaptureListener listener,
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        listener.onToolInvocationFinished(toolCompleted(sessionId, toolCallId, toolName, rawArguments, rawResult));
    }

    private CopilotToolInvocationFinishedEvent toolCompleted(
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        return new CopilotToolInvocationFinishedEvent(
                new CopilotToolSessionContext("run-1", sessionId, "corr-123", "zt01", "main", "sample/runtime"),
                sessionId,
                toolCallId,
                toolName,
                rawArguments,
                CopilotToolInvocationOutcome.COMPLETED,
                rawResult,
                1L,
                null
        );
    }

    private DatabaseToolEvidenceCaptureListener databaseToolEvidenceListener(
            CopilotToolEvidenceSessionStore registry
    ) {
        return new DatabaseToolEvidenceCaptureListener(
                registry,
                new DatabaseToolEvidenceMapper(new JsonPayloadReader(objectMapper))
        );
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
