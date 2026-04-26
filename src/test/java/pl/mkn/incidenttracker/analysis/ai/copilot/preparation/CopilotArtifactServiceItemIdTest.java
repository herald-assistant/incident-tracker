package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.artifactService;

class CopilotArtifactServiceItemIdTest {

    private final CopilotArtifactService artifactService = artifactService(new ObjectMapper());
    private final CopilotToolAccessPolicyFactory policyFactory =
            new CopilotToolAccessPolicyFactory(new CopilotEvidenceCoverageEvaluator());

    @Test
    void shouldRenderStableItemIdsInMarkdownArtifacts() {
        var request = request(List.of(new AnalysisEvidenceSection(
                "elasticsearch",
                "logs",
                List.of(
                        item("log 1", attr("message", "first timeout")),
                        item("log 2", attr("message", "second timeout"))
                )
        )));
        var artifacts = artifactService.toArtifactContentMap(request, policy(request));

        var firstRender = artifacts.get("02-elasticsearch-logs.md");
        var secondRender = artifactService.toArtifactContentMap(request, policy(request)).get("02-elasticsearch-logs.md");

        assertEquals(firstRender, secondRender);
        assertTrue(firstRender.contains("## itemId: elastic-logs-001"));
        assertTrue(firstRender.contains("## itemId: elastic-logs-002"));
        assertTrue(firstRender.contains("Log entry `1`"));
    }

    @Test
    void shouldRenderStableItemIdsInJsonArtifacts() {
        var request = request(List.of(new AnalysisEvidenceSection(
                "custom-provider",
                "diagnostic-events",
                List.of(
                        item("event 1", attr("message", "first")),
                        item("event 2", attr("message", "second"))
                )
        )));
        var artifacts = artifactService.toArtifactContentMap(request, policy(request));

        var content = artifacts.get("02-custom-provider-diagnostic-events.json");

        assertTrue(content.contains("\"itemId\" : \"custom-provider-diagnostic-events-001\""));
        assertTrue(content.contains("\"itemId\" : \"custom-provider-diagnostic-events-002\""));
        assertTrue(content.contains("\"title\" : \"event 1\""));
    }

    private CopilotToolAccessPolicy policy(AnalysisAiAnalysisRequest request) {
        return policyFactory.create(request, List.of());
    }

    private AnalysisAiAnalysisRequest request(List<AnalysisEvidenceSection> sections) {
        return new AnalysisAiAnalysisRequest("corr-123", "dev3", "main", "sample/runtime", sections);
    }

    private AnalysisEvidenceItem item(String title, AnalysisEvidenceAttribute... attributes) {
        return new AnalysisEvidenceItem(title, List.of(attributes));
    }

    private AnalysisEvidenceAttribute attr(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value);
    }
}
