package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Finding;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotQualityDtos.Report;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualityProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.quality.CopilotResponseQualitySeverity;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSessionMetricsRegistryTest {

    @Test
    void shouldCreateAndAggregateMetricsPerCopilotSessionId() {
        var registry = new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
        var context = sessionContext();

        registry.recordPreparation(
                context,
                requestWithEvidence(),
                List.of(artifact("00-incident-manifest.json", "manifest"), artifact("01-logs.md", "log-content")),
                "prepared prompt",
                12L
        );
        registry.recordExecutionDurations(context.copilotSessionId(), 1L, 2L, 3L, 4L);
        registry.recordAssistantUsage(
                context.copilotSessionId(),
                "gpt-5.4",
                1200D,
                340D,
                100D,
                25D,
                1.5D,
                900D
        );
        registry.recordAssistantUsage(
                context.copilotSessionId(),
                "gpt-5.4",
                300D,
                60D,
                null,
                null,
                0.4D,
                250D
        );
        registry.recordSessionUsageInfo(context.copilotSessionId(), 128000D, 15600D, 8D);
        registry.recordToolCall(CopilotToolMetrics.from(
                context.analysisRunId(),
                context.copilotSessionId(),
                "tool-1",
                "gitlab_read_repository_file_chunk",
                7L,
                "gitlab-result"
        ));
        registry.recordToolCall(CopilotToolMetrics.from(
                context.analysisRunId(),
                context.copilotSessionId(),
                "tool-2",
                "db_execute_readonly_sql",
                8L,
                "database-result"
        ));
        registry.recordResponse(
                context.copilotSessionId(),
                true,
                false,
                "DOWNSTREAM_TIMEOUT",
                null
        );
        registry.recordQualityReport(
                context.copilotSessionId(),
                new Report(
                        true,
                        CopilotResponseQualityProperties.Mode.REPORT_ONLY,
                        false,
                        List.of(new Finding(
                                CopilotResponseQualitySeverity.WARNING,
                                "GENERIC_RECOMMENDED_ACTION",
                                "recommendedAction",
                                "Action is too generic."
                        ))
                )
        );

        var metrics = registry.snapshot(context.copilotSessionId()).orElseThrow();

        assertEquals("run-1", metrics.analysisRunId());
        assertEquals("analysis-run-1", metrics.copilotSessionId());
        assertEquals("corr-123", metrics.correlationId());
        assertEquals(2, metrics.evidenceSectionCount());
        assertEquals(3, metrics.evidenceItemCount());
        assertEquals(2, metrics.artifactCount());
        assertEquals("manifest".length() + "log-content".length(), metrics.artifactTotalCharacters());
        assertEquals("prepared prompt".length(), metrics.promptCharacters());
        assertEquals(12L, metrics.preparationDurationMs());
        assertEquals(1L, metrics.clientStartDurationMs());
        assertEquals(2L, metrics.createSessionDurationMs());
        assertEquals(3L, metrics.sendAndWaitDurationMs());
        assertEquals(4L, metrics.totalExecutionDurationMs());
        assertEquals(1500L, metrics.usage().inputTokens());
        assertEquals(400L, metrics.usage().outputTokens());
        assertEquals(1900L, metrics.usage().totalTokens());
        assertEquals(100L, metrics.usage().cacheReadTokens());
        assertEquals(25L, metrics.usage().cacheWriteTokens());
        assertEquals(2, metrics.usage().apiCallCount());
        assertEquals("gpt-5.4", metrics.usage().model());
        assertEquals(128000L, metrics.usage().contextTokenLimit());
        assertEquals(15600L, metrics.usage().contextCurrentTokens());
        assertEquals(8L, metrics.usage().contextMessages());
        assertEquals(2, metrics.totalToolCalls());
        assertEquals(1, metrics.gitLabToolCalls());
        assertEquals(1, metrics.databaseToolCalls());
        assertEquals(0, metrics.elasticToolCalls());
        assertEquals(0, metrics.gitLabReadFileCalls());
        assertEquals(1, metrics.gitLabReadChunkCalls());
        assertEquals("gitlab-result".length(), metrics.gitLabReturnedCharacters());
        assertEquals(1, metrics.databaseQueryCalls());
        assertEquals(1, metrics.databaseRawSqlCalls());
        assertEquals("database-result".length(), metrics.databaseReturnedCharacters());
        assertTrue(metrics.structuredResponse());
        assertEquals("DOWNSTREAM_TIMEOUT", metrics.detectedProblem());
        assertTrue(metrics.qualityGateEnabled());
        assertEquals(CopilotResponseQualityProperties.Mode.REPORT_ONLY, metrics.qualityGateMode());
        assertEquals(1, metrics.qualityFindingCount());
        assertEquals("GENERIC_RECOMMENDED_ACTION", metrics.qualityFindings().get(0).code());
    }

    @Test
    void shouldRemoveSessionStateAfterSummarySnapshot() {
        var registry = new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
        var context = sessionContext();

        registry.recordPreparation(context, requestWithEvidence(), List.of(), "prompt", 1L);

        assertEquals(1, registry.sessionCount());
        assertTrue(registry.remove(context.copilotSessionId()).isPresent());
        assertEquals(0, registry.sessionCount());
        assertTrue(registry.snapshot(context.copilotSessionId()).isEmpty());
    }

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime"
        );
    }

    private AnalysisAiAnalysisRequest requestWithEvidence() {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime",
                List.of(
                        new AnalysisEvidenceSection(
                                "elasticsearch",
                                "logs",
                                List.of(
                                        new AnalysisEvidenceItem(
                                                "log 1",
                                                List.of(new AnalysisEvidenceAttribute("message", "timeout"))
                                        ),
                                        new AnalysisEvidenceItem(
                                                "log 2",
                                                List.of(new AnalysisEvidenceAttribute("message", "retry"))
                                        )
                                )
                        ),
                        new AnalysisEvidenceSection(
                                "gitlab",
                                "resolved-code",
                                List.of(new AnalysisEvidenceItem(
                                        "code",
                                        List.of(new AnalysisEvidenceAttribute("filePath", "src/App.java"))
                                ))
                        )
                )
        );
    }

    private CopilotArtifactService.Artifact artifact(String name, String content) {
        return new CopilotArtifactService.Artifact(
                name,
                "role",
                null,
                null,
                null,
                "text/plain",
                content
        );
    }
}
