package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotPromptRendererTest {

    private final CopilotPromptRenderer renderer = new CopilotPromptRenderer();

    @Test
    void shouldRenderIncidentPromptWithArtifactsAndEnabledCapabilityGroups() {
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "sample/runtime",
                List.of()
        );
        var policy = new CopilotToolAccessPolicy(
                List.of(),
                List.of(
                        "elastic_search_logs_by_correlation_id",
                        "gitlab_find_flow_context",
                        "db_get_scope"
                ),
                true,
                true,
                true,
                true,
                CopilotEvidenceCoverageReport.empty()
        );
        var artifacts = List.of(
                new CopilotArtifactService.Artifact(
                        "00-incident-manifest.json",
                        "Artifact index and analysis context",
                        null,
                        null,
                        null,
                        "application/json",
                        "{\"readFirst\":\"00-incident-manifest.json\"}"
                ),
                new CopilotArtifactService.Artifact(
                        "01-incident-digest.md",
                        "Compressed incident digest for fast grounding",
                        "copilot",
                        "incident-digest",
                        null,
                        "text/markdown",
                        "# Incident digest"
                )
        );

        var prompt = renderer.render(request, policy, artifacts);

        assertTrue(prompt.contains("You are helping with an enterprise software incident analysis."));
        assertTrue(prompt.contains("- correlationId: corr-123"));
        assertTrue(prompt.contains("- environment: dev3"));
        assertTrue(prompt.contains("- gitLabBranch: main"));
        assertTrue(prompt.contains("- gitLabGroup: sample/runtime"));
        assertTrue(prompt.contains("Return only valid JSON."));
        assertTrue(prompt.contains("\"evidenceReferences\": ["));
        assertTrue(prompt.contains("- `00-incident-manifest.json`: Artifact index and analysis context"));
        assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 00-incident-manifest.json | mimeType=application/json>>>"));
        assertTrue(prompt.contains("{\"readFirst\":\"00-incident-manifest.json\"}"));
        assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 01-incident-digest.md | mimeType=text/markdown>>>"));
        assertTrue(prompt.contains("- Elasticsearch logs: fetch additional logs only for listed log coverage gaps for the current incident correlationId."));
        assertTrue(prompt.contains("- GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow or DB code-grounding gaps."));
        assertTrue(prompt.contains("- Database diagnostics: before table/column discovery, ground table and relation hints from deterministic GitLab evidence or enabled GitLab tools when `DB_CODE_GROUNDING_NEEDED` is listed"));
        assertTrue(prompt.contains("Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom"));
    }

    @Test
    void shouldRenderPlaceholdersWhenSessionContextAndToolsAreMissing() {
        var request = new AnalysisAiAnalysisRequest(
                "corr-blank",
                null,
                "",
                null,
                List.of()
        );

        var prompt = renderer.render(request, CopilotToolAccessPolicy.empty(), List.of());

        assertTrue(prompt.contains("- environment: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabBranch: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabGroup: <not-configured>"));
        assertTrue(prompt.contains("Artifacts:\n- none"));
        assertTrue(prompt.contains("Embedded artifact contents:\n<none>"));
        assertTrue(prompt.contains("Available capability groups:\n- none; rely on the incident artifacts for this session."));
    }
}
