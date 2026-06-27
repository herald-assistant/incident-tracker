package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageReport;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentPromptRendererTest {

    private final CopilotIncidentPromptRenderer renderer = new CopilotIncidentPromptRenderer();

    @Test
    void shouldRenderIncidentPromptWithArtifactsAndEnabledCapabilityGroups() {
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "CRM/runtime",
                List.of()
        );
        var policy = new CopilotIncidentToolAccessPolicy(
                List.of(),
                List.of(
                        "elastic_search_logs_by_correlation_id",
                        "gitlab_find_flow_context",
                        "db_get_scope",
                        "opctx_search",
                        "record_tool_feedback"
                ),
                true,
                true,
                true,
                true,
                CopilotIncidentEvidenceCoverageReport.empty()
        );
        var artifacts = List.of(
                new CopilotRenderedArtifact(
                        "00-incident-manifest.json",
                        "Artifact index and analysis context",
                        null,
                        null,
                        null,
                        "application/json",
                        "{\"readFirst\":\"00-incident-manifest.json\"}"
                ),
                new CopilotRenderedArtifact(
                        "01-incident-digest.md",
                        "Compressed incident digest for fast grounding",
                        "copilot",
                        "incident-digest",
                        null,
                        "text/markdown",
                        "# Incident digest"
                )
        );

        var prompt = renderer.render(request, policy, sessionConfigRequest(), artifacts);

        assertTrue(prompt.contains("You are helping with an enterprise software incident analysis."));
        assertTrue(prompt.contains("- correlationId: corr-123"));
        assertTrue(prompt.contains("- environment: dev3"));
        assertTrue(prompt.contains("- gitLabBranch: main"));
        assertTrue(prompt.contains("- gitLabGroup: CRM/runtime"));
        assertTrue(prompt.contains("Return only valid JSON."));
        assertTrue(prompt.contains("The final answer must start with `{` and end with `}`."));
        assertTrue(prompt.contains("Do not add status text such as \"I have all the evidence needed\" before the JSON."));
        assertTrue(prompt.contains("\"functionalAnalysis\": \"markdown string in Polish, Functional Analysis v1\""));
        assertTrue(prompt.contains("\"technicalAnalysis\": \"markdown string in Polish, Technical Handoff v1\""));
        assertTrue(prompt.contains("- `00-incident-manifest.json`: Artifact index and analysis context"));
        assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 00-incident-manifest.json | mimeType=application/json>>>"));
        assertTrue(prompt.contains("{\"readFirst\":\"00-incident-manifest.json\"}"));
        assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 01-incident-digest.md | mimeType=text/markdown>>>"));
        assertTrue(prompt.contains("- Elasticsearch logs: fetch additional logs only for listed log coverage gaps for the current incident correlationId."));
        assertTrue(prompt.contains("- GitLab code: inspect class references/imports, method slices, focused chunks, outlines or flow context only for listed code, flow, technical-analysis or DB code-grounding gaps."));
        assertTrue(prompt.contains("GitLab tools do not read branch/group from hidden ToolContext."));
        assertTrue(prompt.contains("pass `branchRef` explicitly from `gitLabBranch`"));
        assertTrue(prompt.contains("Pass known `projectName` values"));
        assertTrue(prompt.contains("Do not pass `gitLabGroup` to GitLab tools"));
        assertTrue(prompt.contains("Pass explicit `branchRef` from `gitLabBranch`, known `projectName`, and optional `applicationName`; do not pass `gitLabGroup`."));
        assertTrue(prompt.contains("- Database diagnostics: before table/column discovery, ground table and relation hints from deterministic GitLab evidence or enabled GitLab tools when `DB_CODE_GROUNDING_NEEDED` is listed"));
        assertTrue(prompt.contains("- Operational context catalog: browse or search systems, repositories, code-search scopes, processes, integrations, bounded contexts, teams, glossary terms and handoff rules"));
        assertTrue(prompt.contains("The platform tool `record_tool_feedback` is available for visible tool-quality feedback."));
        assertTrue(prompt.contains("- Tool quality feedback: use `record_tool_feedback` only for important tool-result quality signals"));
        assertTrue(prompt.contains("Operational Context tools provide catalog context"));
        assertTrue(prompt.contains("The built-in `skill` tool is enabled for runtime skills."));
        assertTrue(prompt.contains("At the start of the initial diagnosis, load the starter skill `incident-analysis-orchestrator`"));
        assertTrue(prompt.contains("Do not claim that you know a skill definition, SKILL.md content, or detailed skill rules unless you loaded that skill through the `skill` tool in this session."));
        assertTrue(prompt.contains("Runtime skills:"));
        assertTrue(prompt.contains("starter skill to load before classifying the incident: `incident-analysis-orchestrator`"));
        assertTrue(prompt.contains("diagnostic skills to load only when required by the starter algorithm: incident-operational-context-tools, incident-analysis-gitlab-tools, incident-data-diagnostics"));
        assertTrue(prompt.contains("result skills to load after diagnosis for final answer synthesis: incident-functional-analysis, incident-technical-handoff"));
        assertTrue(prompt.contains("Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom"));
        assertTrue(prompt.contains("Treat `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED` as a targeted gap for `functionalAnalysis`"));
        assertTrue(prompt.contains("Treat `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` as a targeted gap for `technicalAnalysis`"));
    }

    @Test
    void shouldRenderPlaceholdersWhenSessionContextAndToolsAreMissing() {
        var request = new InitialAnalysisRequest(
                "corr-blank",
                null,
                "",
                null,
                List.of()
        );

        var prompt = renderer.render(request, CopilotIncidentToolAccessPolicy.empty(), null, List.of());

        assertTrue(prompt.contains("- environment: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabBranch: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabGroup: <not-configured>"));
        assertTrue(prompt.contains("The built-in `skill` tool is not enabled for this session."));
        assertTrue(prompt.contains("Runtime skills:\n- built-in `skill` tool is unavailable"));
        assertTrue(prompt.contains("Artifacts:\n- none"));
        assertTrue(prompt.contains("Embedded artifact contents:\n<none>"));
        assertTrue(prompt.contains("Available capability groups:\n- none; rely on the incident artifacts for this session."));
    }

    private CopilotSessionConfigRequest sessionConfigRequest() {
        return new CopilotSessionConfigRequest(
                "session-123",
                List.of(),
                List.of("gitlab_find_flow_context"),
                List.of("copilot-skills/incident"),
                null,
                "Denied"
        );
    }
}
