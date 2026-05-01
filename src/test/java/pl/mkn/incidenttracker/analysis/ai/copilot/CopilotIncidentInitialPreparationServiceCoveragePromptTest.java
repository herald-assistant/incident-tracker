package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialRunAssembler;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentPromptRenderer;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicyFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session.CopilotMetricsProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;

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
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.metricsLogger;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.sessionTelemetry;

class CopilotIncidentInitialPreparationServiceCoveragePromptTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRenderEvidenceCoverageInManifestAndPromptToolInstructions() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var factory = mock(CopilotSdkToolFactory.class);
        when(factory.createToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(List.of(
                tool("gitlab_search_repository_candidates"),
                tool("gitlab_find_flow_context"),
                tool("gitlab_read_repository_file_chunk")
        ));
        var service = new CopilotIncidentInitialPreparationService(
                new CopilotIncidentInitialRunAssembler(
                        factory,
                        new CopilotIncidentToolSessionContextFactory(new CopilotIncidentHiddenToolContextFactory()),
                        new CopilotIncidentSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties)),
                        artifactService(objectMapper),
                        new CopilotIncidentToolAccessPolicyFactory(new CopilotIncidentEvidenceCoverageEvaluator()),
                        new CopilotIncidentPromptRenderer(),
                        new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper())
                ),
                new CopilotRunPreparationService(
                        new CopilotPreparedSessionFactory(new CopilotSessionConfigFactory(properties))
                ),
                sessionTelemetry(
                        new CopilotSessionMetricsRegistry(new CopilotMetricsProperties()),
                        metricsLogger(objectMapper)
                )
        );

        try (var prepared = service.prepare(failingMethodOnlyRequest())) {
            assertTrue(prepared.session().sessionConfig().getAvailableTools().contains("gitlab_find_flow_context"));
            assertTrue(prepared.session().sessionConfig().getAvailableTools().contains("gitlab_read_repository_file_chunk"));
            assertFalse(prepared.session().sessionConfig().getAvailableTools().contains("gitlab_search_repository_candidates"));

            var prompt = prepared.prompt();
            assertTrue(prompt.contains("Use tools only for evidence gaps listed in `evidenceCoverage.gaps`"));
            assertTrue(prompt.contains("Do not use tools just because they are available."));
            assertTrue(prompt.contains("coverage-aware and may be enabled for targeted gap filling"));
            assertFalse(prompt.contains("GitLab and Elasticsearch tools are fallback-only"));
            assertTrue(prompt.contains("GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow, affected-function or DB code-grounding gaps."));

            var manifest = prepared.session().artifactContents().get("00-incident-manifest.json");
            assertTrue(manifest.contains("\"evidenceCoverage\""));
            assertTrue(manifest.contains("\"gitLab\" : \"FAILING_METHOD_ONLY\""));
            assertTrue(manifest.contains("\"code\" : \"MISSING_FLOW_CONTEXT\""));
            assertTrue(manifest.contains("\"code\" : \"AFFECTED_FUNCTION_GITLAB_RECOMMENDED\""));
            assertTrue(manifest.contains("\"enabledToolNames\""));
            assertTrue(manifest.contains("\"gitlab_find_flow_context\""));
            assertFalse(manifest.contains("\"gitlab_search_repository_candidates\""));
        }
    }

    private InitialAnalysisRequest failingMethodOnlyRequest() {
        return new InitialAnalysisRequest(
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
