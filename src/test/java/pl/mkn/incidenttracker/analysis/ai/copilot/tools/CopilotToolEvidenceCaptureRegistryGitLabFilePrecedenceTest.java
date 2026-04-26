package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolEvidenceCaptureRegistryGitLabFilePrecedenceTest {

    private static final String FILE_PATH = "src/main/java/pl/mkn/orders/OrderService.java";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepFullGitLabFileWhenChunkForSameFileArrivesLater() {
        var registry = new CopilotToolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        var updateCount = new AtomicInteger();
        registry.registerSession("session-1", new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
                updateCount.incrementAndGet();
            }
        });

        registry.captureToolResult(
                "session-1",
                "tool-call-chunk-1",
                "gitlab_read_repository_file_chunk",
                "{}",
                chunkResult("focused chunk")
        );
        assertEquals("gitlab_read_repository_file_chunk", attributes(capturedSection.get()).get("toolName"));

        registry.captureToolResult(
                "session-1",
                "tool-call-full-1",
                "gitlab_read_repository_file",
                "{}",
                fullFileResult("full file content")
        );
        assertEquals("gitlab_read_repository_file", attributes(capturedSection.get()).get("toolName"));
        assertEquals("full file content", attributes(capturedSection.get()).get("content"));

        registry.captureToolResult(
                "session-1",
                "tool-call-chunk-2",
                "gitlab_read_repository_file_chunk",
                "{}",
                chunkResult("later focused chunk")
        );

        var attributes = attributes(capturedSection.get());
        assertEquals(3, updateCount.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-fetched-code", capturedSection.get().category());
        assertEquals("gitlab_read_repository_file", attributes.get("toolName"));
        assertEquals("full file content", attributes.get("content"));
        assertFalse(attributes.containsKey("requestedStartLine"));
        assertTrue(attributes.get("filePath").contains("OrderService.java"));
    }

    private String fullFileResult(String content) {
        return """
                {
                  "group": "sample/runtime",
                  "projectName": "orders-api",
                  "branch": "main",
                  "filePath": "%s",
                  "content": "%s",
                  "truncated": false
                }
                """.formatted(FILE_PATH, content);
    }

    private String chunkResult(String content) {
        return """
                {
                  "group": "sample/runtime",
                  "projectName": "orders-api",
                  "branch": "main",
                  "filePath": "%s",
                  "requestedStartLine": 5,
                  "requestedEndLine": 10,
                  "returnedStartLine": 5,
                  "returnedEndLine": 10,
                  "totalLines": 100,
                  "content": "%s",
                  "truncated": false
                }
                """.formatted(FILE_PATH, content);
    }

    private Map<String, String> attributes(AnalysisEvidenceSection section) {
        return section.items().get(0).attributes().stream()
                .collect(Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value()
                ));
    }
}
