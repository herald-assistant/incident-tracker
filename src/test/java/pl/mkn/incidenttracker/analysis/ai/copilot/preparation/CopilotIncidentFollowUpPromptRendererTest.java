package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatAnalysisSnapshot;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatTurn;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageReport;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;

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
                        "Podsumowanie",
                        "Timeout downstream",
                        "Sprawdzic integracje",
                        "Logi potwierdzaja timeout",
                        "Obsluga platnosci",
                        "Payment",
                        "Checkout",
                        "Team A"
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
                        "db_get_scope"
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

        var prompt = renderer.render(request, policy, artifacts);

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
        assertTrue(prompt.contains("- GitLab code: inspect repository candidates, class references, outlines and focused file chunks in the fixed group and branch."));
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

        var prompt = renderer.render(request, CopilotIncidentToolAccessPolicy.empty(), List.of());

        assertTrue(prompt.contains("- environment: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabBranch: <not-resolved-from-logs>"));
        assertTrue(prompt.contains("- gitLabGroup: <not-configured>"));
        assertTrue(prompt.contains("Final analysis result:\n- brak zapisanego wyniku koncowego"));
        assertTrue(prompt.contains("Previous chat history:\n- brak wczesniejszych wiadomosci"));
        assertTrue(prompt.contains("Artifacts:\n- none"));
        assertTrue(prompt.contains("Embedded artifact contents:\n<none>"));
        assertTrue(prompt.contains("Available capability groups:\n- none; answer only from existing analysis evidence and chat history."));
    }
}
