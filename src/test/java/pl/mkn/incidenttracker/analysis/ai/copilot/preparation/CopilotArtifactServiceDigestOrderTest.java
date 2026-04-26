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

class CopilotArtifactServiceDigestOrderTest {

    private final CopilotArtifactService artifactService = new CopilotArtifactService(new ObjectMapper());
    private final CopilotToolAccessPolicyFactory policyFactory =
            new CopilotToolAccessPolicyFactory(new CopilotEvidenceCoverageEvaluator());

    @Test
    void shouldRenderManifestThenDigestThenEvidenceArtifacts() {
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "sample/runtime",
                List.of(
                        section("elasticsearch", "logs"),
                        section("deployment-context", "resolved-deployment"),
                        section("dynatrace", "runtime-signals"),
                        section("gitlab", "resolved-code"),
                        section("operational-context", "matched-context")
                )
        );

        var artifacts = artifactService.renderArtifacts(request, policyFactory.create(request, List.of()));

        assertEquals("00-incident-manifest.json", artifacts.get(0).displayName());
        assertEquals("01-incident-digest.md", artifacts.get(1).displayName());
        assertEquals("02-elasticsearch-logs.md", artifacts.get(2).displayName());
        assertEquals("03-deployment-context-resolved-deployment.json", artifacts.get(3).displayName());
        assertEquals("04-dynatrace-runtime-signals.md", artifacts.get(4).displayName());
        assertEquals("05-gitlab-resolved-code.md", artifacts.get(5).displayName());
        assertEquals("06-operational-context-matched-context.json", artifacts.get(6).displayName());

        var manifest = artifacts.get(0).content();
        assertTrue(manifest.contains("\"readNext\" : \"01-incident-digest.md\""));
        assertTrue(manifest.contains("\"artifactFormatVersion\" : \"copilot-artifacts-v2\""));
        assertTrue(manifest.contains("\"displayName\" : \"01-incident-digest.md\""));
        assertTrue(manifest.contains("\"itemIds\""));
    }

    private AnalysisEvidenceSection section(String provider, String category) {
        return new AnalysisEvidenceSection(
                provider,
                category,
                List.of(new AnalysisEvidenceItem(
                        provider + " item",
                        List.of(new AnalysisEvidenceAttribute("message", "value"))
                ))
        );
    }
}
