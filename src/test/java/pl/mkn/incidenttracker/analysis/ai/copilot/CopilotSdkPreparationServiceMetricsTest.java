package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotInitialAnalysisRunAssembler;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotPromptRenderer;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotToolAccessPolicyFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.context.CopilotToolSessionContext;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.artifactService;

class CopilotSdkPreparationServiceMetricsTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecordPromptAndArtifactSizesDeterministically() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var metricsRegistry = new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
        var factory = mock(CopilotSdkToolFactory.class);
        when(factory.createToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(List.<ToolDefinition>of());
        var service = new CopilotSdkPreparationService(
                new CopilotInitialAnalysisRunAssembler(
                        factory,
                        new CopilotIncidentSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties)),
                        artifactService(objectMapper),
                        new CopilotToolAccessPolicyFactory(new CopilotEvidenceCoverageEvaluator()),
                        new CopilotPromptRenderer(),
                        new CopilotIncidentHiddenToolContextFactory()
                ),
                new CopilotPreparedSessionFactory(new CopilotSessionConfigFactory(properties)),
                metricsRegistry
        );

        try (var prepared = service.prepare(requestWithEvidence())) {
            var metrics = metricsRegistry.snapshot(prepared.session().sessionConfig().getSessionId()).orElseThrow();

            assertEquals(2, metrics.evidenceSectionCount());
            assertEquals(3, metrics.evidenceItemCount());
            assertEquals(4, metrics.artifactCount());
            assertEquals(
                    prepared.session().artifactContents().values().stream().mapToLong(String::length).sum(),
                    metrics.artifactTotalCharacters()
            );
            assertEquals(prepared.prompt().length(), metrics.promptCharacters());
            assertTrue(metrics.preparationDurationMs() >= 0L);
        }
    }

    private InitialAnalysisRequest requestWithEvidence() {
        return new InitialAnalysisRequest(
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(
                                        item("log 1", "message", "timeout"),
                                        item("log 2", "message", "retry")
                                )
                        ),
                        new AnalysisEvidenceSection(
                                "operational-context",
                                "matched-context",
                                List.of(item("context", "team", "Core"))
                        )
                )
        );
    }

    private AnalysisEvidenceItem item(String title, String name, String value) {
        return new AnalysisEvidenceItem(
                title,
                List.of(new AnalysisEvidenceAttribute(name, value))
        );
    }
}
