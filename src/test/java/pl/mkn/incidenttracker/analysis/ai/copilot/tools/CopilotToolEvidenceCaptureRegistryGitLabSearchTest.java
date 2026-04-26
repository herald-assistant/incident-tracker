package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotToolEvidenceCaptureRegistryGitLabSearchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotExposeGitLabSearchFlowOrOutlineCallsAsUserEvidence() {
        var registry = toolEvidenceCaptureRegistry(objectMapper);
        var capturedSection = new AtomicReference<AnalysisEvidenceSection>();
        registry.registerSession("session-1", listener(capturedSection));

        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-search-1",
                "gitlab_search_repository_candidates",
                "{\"reason\":\"Szukam kandydatow plikow.\"}",
                "{\"candidates\":[]}"
        );
        registry.captureToolResult(
                "session-1",
                "tool-call-gitlab-flow-1",
                "gitlab_find_flow_context",
                "{\"reason\":\"Szukam kontekstu przeplywu.\"}",
                "{\"groups\":[],\"recommendedNextReads\":[]}"
        );
        registry.captureToolResult(
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
                          "imports": [],
                          "classes": [],
                          "annotations": [],
                          "methodSignatures": [],
                          "inferredRole": "service-or-orchestrator",
                          "truncated": false
                        }
                        """
        );

        assertNull(capturedSection.get());
    }

    private AnalysisAiToolEvidenceListener listener(AtomicReference<AnalysisEvidenceSection> capturedSection) {
        return new AnalysisAiToolEvidenceListener() {
            @Override
            public void onToolEvidenceUpdated(AnalysisEvidenceSection section) {
                capturedSection.set(section);
            }
        };
    }
}
