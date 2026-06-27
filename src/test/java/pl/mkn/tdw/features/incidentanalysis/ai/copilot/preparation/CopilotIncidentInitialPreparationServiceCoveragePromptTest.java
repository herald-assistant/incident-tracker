package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialRunAssembler;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentPromptRenderer;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicyFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.artifactService;

class CopilotIncidentInitialPreparationServiceCoveragePromptTest {

    private static final CopilotToolDescriptionContext INCIDENT_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRenderEvidenceCoverageInManifestAndPromptToolInstructions() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var factory = mock(CopilotSdkToolFactory.class);
        when(factory.createToolDefinitions(
                any(CopilotToolSessionContext.class),
                eq(INCIDENT_DESCRIPTION_CONTEXT)
        )).thenReturn(List.of(
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
            assertTrue(prompt.contains("GitLab code: inspect class references/imports, method slices, focused chunks, outlines or flow context only for listed code, flow, technical-analysis or DB code-grounding gaps."));
            assertTrue(prompt.contains("Pass explicit `branchRef` from `gitLabBranch`, known `projectName`, and optional `applicationName`; do not pass `gitLabGroup`."));

            var manifest = prepared.session().artifactContents().get("00-incident-manifest.json");
            assertTrue(manifest.contains("\"evidenceCoverage\""));
            assertTrue(manifest.contains("\"gitLab\" : \"FAILING_METHOD_ONLY\""));
            assertTrue(manifest.contains("\"code\" : \"MISSING_FLOW_CONTEXT\""));
            assertTrue(manifest.contains("\"code\" : \"TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED\""));
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
                "CRM/runtime",
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
                                                    Customer submit(CheckoutCommand command) {
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
