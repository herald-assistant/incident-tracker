package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotPromptRenderer;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSessionConfigFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotToolAccessPolicyFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.artifactService;

class CopilotSdkPreparationServiceCoveragePromptTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRenderEvidenceCoverageInManifestAndPromptToolInstructions() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var bridge = mock(CopilotSdkToolBridge.class);
        when(bridge.buildToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(List.of(
                tool("gitlab_search_repository_candidates"),
                tool("gitlab_find_flow_context"),
                tool("gitlab_read_repository_file_chunk")
        ));
        var service = new CopilotSdkPreparationService(
                bridge,
                new CopilotSkillRuntimeLoader(properties),
                artifactService(objectMapper),
                new CopilotToolAccessPolicyFactory(new CopilotEvidenceCoverageEvaluator()),
                new CopilotPromptRenderer(),
                new CopilotSessionConfigFactory(properties),
                new CopilotSessionMetricsRegistry(new CopilotMetricsProperties())
        );

        try (var prepared = service.prepare(failingMethodOnlyRequest())) {
            assertTrue(prepared.sessionConfig().getAvailableTools().contains("gitlab_find_flow_context"));
            assertTrue(prepared.sessionConfig().getAvailableTools().contains("gitlab_read_repository_file_chunk"));
            assertFalse(prepared.sessionConfig().getAvailableTools().contains("gitlab_search_repository_candidates"));

            var prompt = prepared.prompt();
            assertTrue(prompt.contains("Use tools only for evidence gaps listed in `evidenceCoverage.gaps`"));
            assertTrue(prompt.contains("Do not use tools just because they are available."));
            assertTrue(prompt.contains("coverage-aware and may be enabled for targeted gap filling"));
            assertFalse(prompt.contains("GitLab and Elasticsearch tools are fallback-only"));
            assertTrue(prompt.contains("GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow or DB code-grounding gaps."));

            var manifest = prepared.artifactContents().get("00-incident-manifest.json");
            assertTrue(manifest.contains("\"evidenceCoverage\""));
            assertTrue(manifest.contains("\"gitLab\" : \"FAILING_METHOD_ONLY\""));
            assertTrue(manifest.contains("\"code\" : \"MISSING_FLOW_CONTEXT\""));
            assertTrue(manifest.contains("\"enabledToolNames\""));
            assertTrue(manifest.contains("\"gitlab_find_flow_context\""));
            assertFalse(manifest.contains("\"gitlab_search_repository_candidates\""));
        }
    }

    private AnalysisAiAnalysisRequest failingMethodOnlyRequest() {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                List.of(new AnalysisEvidenceSection(
                        "gitlab",
                        "resolved-code",
                        List.of(new AnalysisEvidenceItem(
                                "CheckoutService.java around failing method",
                                List.of(
                                        attr("projectName", "checkout-service"),
                                        attr("filePath", "src/main/java/com/example/CheckoutService.java"),
                                        attr("lineNumber", "12"),
                                        attr("returnedStartLine", "8"),
                                        attr("returnedEndLine", "18"),
                                        attr("content", """
                                                class CheckoutService {
                                                    Order submit(CheckoutCommand command) {
                                                        if (command == null) {
                                                            throw new IllegalArgumentException("command");
                                                        }
                                                        return command.toOrder();
                                                    }
                                                }
                                                """),
                                        attr("contentTruncated", "false")
                                )
                        ))
                ))
        );
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }

    private AnalysisEvidenceAttribute attr(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }
}
