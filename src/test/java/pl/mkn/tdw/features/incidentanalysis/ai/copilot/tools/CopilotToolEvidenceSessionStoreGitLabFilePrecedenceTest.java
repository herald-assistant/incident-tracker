package pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.tdw.common.JsonPayloadReader;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotToolEvidenceSessionStoreGitLabFilePrecedenceTest {

    private static final String FILE_PATH = "src/main/java/com/example/crm/customer/CustomerService.java";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepFullGitLabFileWhenChunkForSameFileArrivesLater() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var toolEvidenceListener = gitLabToolEvidenceListener(registry);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        var updateCount = new AtomicInteger();
        registry.registerSession("session-1", section -> {
            capturedSection.set(section);
            updateCount.incrementAndGet();
        });

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-chunk-1",
                "gitlab_read_repository_file_chunk",
                "{\"reason\":\"Sprawdzam fragment pliku.\"}",
                chunkResult("focused chunk")
        );
        assertEquals("Sprawdzam fragment pliku.", attributes(capturedSection.get()).get("reason"));
        assertEquals("5", attributes(capturedSection.get()).get("startLine"));

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-full-1",
                "gitlab_read_repository_file",
                "{\"reason\":\"Czytam caly plik.\"}",
                fullFileResult("full file content")
        );
        assertEquals("Czytam caly plik.", attributes(capturedSection.get()).get("reason"));
        assertEquals("full file content", attributes(capturedSection.get()).get("content"));

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-chunk-2",
                "gitlab_read_repository_file_chunk",
                "{\"reason\":\"Sprawdzam pozniejszy fragment.\"}",
                chunkResult("later focused chunk")
        );

        var attributes = attributes(capturedSection.get());
        assertEquals(3, updateCount.get());
        assertEquals("gitlab", capturedSection.get().provider());
        assertEquals("tool-fetched-code", capturedSection.get().category());
        assertEquals("Czytam caly plik.", attributes.get("reason"));
        assertEquals("full file content", attributes.get("content"));
        assertFalse(attributes.containsKey("startLine"));
        assertTrue(attributes.get("filePath").contains("CustomerService.java"));
    }

    @Test
    void shouldCaptureGitLabFilesByPathAsFetchedCodeItems() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var toolEvidenceListener = gitLabToolEvidenceListener(registry);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", capturedSection::set);

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-files-1",
                "gitlab_read_repository_files_by_path",
                "{\"reason\":\"Czytam pliki endpointu.\"}",
                filesByPathResult()
        );

        var section = capturedSection.get();
        assertEquals("gitlab", section.provider());
        assertEquals("tool-fetched-code", section.category());
        assertEquals(2, section.items().size());
        assertEquals(
                "src/main/java/com/example/crm/customer/CustomerController.java",
                attributes(section, 0).get("filePath")
        );
        assertEquals("class CustomerController {}", attributes(section, 0).get("content"));
        assertEquals("Czytam pliki endpointu.", attributes(section, 0).get("reason"));
        assertEquals(
                "src/main/java/com/example/crm/customer/CustomerService.java",
                attributes(section, 1).get("filePath")
        );
        assertFalse(attributes(section, 0).containsKey("startLine"));
    }

    @Test
    void shouldCaptureGitLabJavaMethodSliceAsFetchedCodeItem() {
        var registry = toolEvidenceSessionStore(objectMapper);
        var toolEvidenceListener = gitLabToolEvidenceListener(registry);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", capturedSection::set);

        capture(
                toolEvidenceListener,
                "session-1",
                "tool-call-method-slice-1",
                "gitlab_read_java_method_slice",
                "{\"reason\":\"Sprawdzam metode odpowiedzialna za walidacje.\"}",
                javaMethodSliceResult()
        );

        var section = capturedSection.get();
        var attributes = attributes(section);
        assertEquals("gitlab", section.provider());
        assertEquals("tool-fetched-code", section.category());
        assertEquals(FILE_PATH, attributes.get("filePath"));
        assertEquals("gitlab_read_java_method_slice", attributes.get("toolName"));
        assertEquals("tool-call-method-slice-1", attributes.get("toolCallId"));
        assertEquals("Sprawdzam metode odpowiedzialna za walidacje.", attributes.get("reason"));
        assertEquals("12", attributes.get("startLine"));
        assertTrue(attributes.get("content").contains("void validateCustomer()"));
    }

    private String fullFileResult(String content) {
        return """
                {
                  "group": "CRM/runtime",
                  "projectName": "crm-customer-api",
                  "branch": "main",
                  "filePath": "%s",
                  "content": "%s",
                  "truncated": false
                }
                """.formatted(FILE_PATH, content);
    }

    private String javaMethodSliceResult() {
        return """
                {
                  "group": "CRM/runtime",
                  "projectName": "crm-customer-api",
                  "branch": "main",
                  "filePath": "%s",
                  "status": "FOUND",
                  "declaringTypeName": "CustomerService",
                  "requestedMethods": [
                    {
                      "methodName": "validateCustomer",
                      "lineStart": 12
                    }
                  ],
                  "returnedLineStart": 12,
                  "returnedLineEnd": 18,
                  "totalLines": 120,
                  "content": "class CustomerService {\\n    void validateCustomer() {\\n    }\\n}",
                  "returnedCharacters": 57,
                  "truncated": false,
                  "includedImports": [],
                  "includedFields": [],
                  "includedMethods": [
                    {
                      "declaringTypeName": "CustomerService",
                      "methodName": "validateCustomer",
                      "signature": "void validateCustomer()",
                      "lineStart": 12,
                      "lineEnd": 18,
                      "parameterCount": 0,
                      "parameterTypes": []
                    }
                  ],
                  "omittedFieldCount": 0,
                  "omittedMethodCount": 0,
                  "candidates": [],
                  "limitations": []
                }
                """.formatted(FILE_PATH);
    }

    private String chunkResult(String content) {
        return """
                {
                  "group": "CRM/runtime",
                  "projectName": "crm-customer-api",
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

    private String filesByPathResult() {
        return """
                {
                  "group": "CRM/runtime",
                  "projectName": "crm-customer-api",
                  "branch": "main",
                  "requestedFileCount": 3,
                  "processedFileCount": 3,
                  "returnedFileCount": 2,
                  "failedFileCount": 1,
                  "totalReturnedCharacters": 50,
                  "fileCountTruncated": false,
                  "totalCharacterLimitReached": false,
                  "files": [
                    {
                      "group": "CRM/runtime",
                      "projectName": "crm-customer-api",
                      "branch": "main",
                      "filePath": "src/main/java/com/example/crm/customer/CustomerController.java",
                      "content": "class CustomerController {}",
                      "truncated": false,
                      "inferredRole": "entrypoint",
                      "returnedCharacters": 27,
                      "error": null
                    },
                    {
                      "group": "CRM/runtime",
                      "projectName": "crm-customer-api",
                      "branch": "main",
                      "filePath": "src/main/java/com/example/crm/customer/Missing.java",
                      "content": null,
                      "truncated": false,
                      "inferredRole": "other",
                      "returnedCharacters": 0,
                      "error": "IllegalStateException: file not found"
                    },
                    {
                      "group": "CRM/runtime",
                      "projectName": "crm-customer-api",
                      "branch": "main",
                      "filePath": "src/main/java/com/example/crm/customer/CustomerService.java",
                      "content": "class CustomerService {}",
                      "truncated": false,
                      "inferredRole": "service-or-orchestrator",
                      "returnedCharacters": 23,
                      "error": null
                    }
                  ]
                }
                """;
    }

    private Map<String, String> attributes(AnalysisEvidenceSection section) {
        return attributes(section, 0);
    }

    private Map<String, String> attributes(AnalysisEvidenceSection section, int itemIndex) {
        return section.items().get(itemIndex).attributes().stream()
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
