package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatAnalysisSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatTurn;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageReport;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotIncidentFollowUpPromptRendererTest {

    private final CopilotIncidentFollowUpPromptRenderer renderer = new CopilotIncidentFollowUpPromptRenderer();

    @Test
    void shouldRenderIncidentFollowUpPromptWithChatContextAndArtifacts() {
        var request = new AnalysisAiChatRequest(
                "corr-123",
                "dev3",
                "main",
                "sample/runtime",
                List.of(),
                List.of(),
                new AnalysisAiChatAnalysisSnapshot(
                        "Timeout downstream",
                        "Obsluga platnosci",
                        "Payment",
                        "Checkout",
                        "Analiza funkcjonalna: timeout dotyka obslugi platnosci.",
                        "Analiza techniczna: sprawdz integracje i logi timeoutu.",
                        "medium",
                        List.of("Brak potwierdzenia po stronie downstream.")
                ),
                List.of(new AnalysisAiChatTurn("user", "Co sprawdzic dalej?")),
                "Zweryfikuj kod",
                null
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
        var artifacts = List.of(new CopilotRenderedArtifact(
                "01-incident-digest.md",
                "Compressed incident digest for fast grounding",
                "copilot",
                "incident-digest",
                null,
                "text/markdown",
                "# Incident digest"
        ));

        var prompt = renderer.render(request, policy, sessionConfigRequest(), artifacts);

        assertTrue(prompt.contains("You are continuing an already completed enterprise software incident analysis."));
        assertTrue(prompt.contains("- correlationId: corr-123"));
        assertTrue(prompt.contains("- environment: dev3"));
        assertTrue(prompt.contains("- gitLabBranch: main"));
        assertTrue(prompt.contains("- gitLabGroup: sample/runtime"));
        assertTrue(prompt.contains("<<<BEGIN LATEST USER MESSAGE>>>"));
        assertTrue(prompt.contains("Zweryfikuj kod"));
        assertTrue(prompt.contains("- detectedProblem: Timeout downstream"));
        assertTrue(prompt.contains("### user"));
        assertTrue(prompt.contains("Co sprawdzic dalej?"));
        assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 01-incident-digest.md | mimeType=text/markdown>>>"));
        assertTrue(prompt.contains("The built-in `skill` tool is enabled for runtime skills."));
        assertTrue(prompt.contains("Use it to load available incident runtime skills from `00-incident-manifest.json` under `runtimeSkills.preferredSkillNames` whenever the latest user request depends on skill rules or asks about skill definitions."));
        assertTrue(prompt.contains("If the user asks what skills are available or where a skill definition came from, answer only after using the `skill` tool"));
        assertTrue(prompt.contains("load `incident-technical-handoff` through the `skill` tool when available and answer directly in Markdown using `Technical Handoff v1`"));
        assertTrue(prompt.contains("Runtime skills:"));
        assertTrue(prompt.contains("preferred skills to load when relevant to the latest user request: incident-analysis-core, incident-functional-analysis, incident-technical-handoff"));
        assertTrue(prompt.contains("A technical handoff must keep the required section order"));
        assertTrue(prompt.contains("- GitLab code: inspect repository candidates, class references, outlines and focused file chunks in the fixed group and branch."));
        assertTrue(prompt.contains("- Operational context catalog: browse or search reusable catalog context"));
        assertTrue(prompt.contains("The platform tool `record_tool_feedback` is available for visible tool-quality feedback."));
        assertTrue(prompt.contains("- Tool quality feedback: use `record_tool_feedback` only for important tool-result quality signals"));
    }

    @Test
    void shouldRenderPlaceholdersWhenFollowUpContextIsMissing() {
        var request = new AnalysisAiChatRequest(
                "corr-blank",
                null,
                "",
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null
        );

        var prompt = renderer.render(request, CopilotIncidentToolAccessPolicy.empty(), null, List.of());

        assertTrue(prompt.contains("- environment: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabBranch: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabGroup: <not-configured>"));
        assertTrue(prompt.contains("The built-in `skill` tool is not enabled for this session."));
        assertTrue(prompt.contains("Runtime skills:\n- built-in `skill` tool is unavailable"));
        assertTrue(prompt.contains("Final analysis result:\n- brak zapisanego wyniku koncowego"));
        assertTrue(prompt.contains("Previous chat history:\n- brak wczesniejszych wiadomosci"));
        assertTrue(prompt.contains("Artifacts:\n- none"));
        assertTrue(prompt.contains("Embedded artifact contents:\n<none>"));
        assertTrue(prompt.contains("Available capability groups:\n- none; answer only from existing analysis evidence and chat history."));
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
