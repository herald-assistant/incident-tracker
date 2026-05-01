package pl.mkn.incidenttracker.analysis.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotFollowUpArtifactRequestFactory;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotFollowUpArtifactRequestFactoryTest {

    private final CopilotFollowUpArtifactRequestFactory factory = new CopilotFollowUpArtifactRequestFactory();

    @Test
    void shouldCreateInitialAnalysisRequestForFollowUpArtifacts() {
        var deterministicEvidence = section("elasticsearch", "logs", "log evidence");
        var toolEvidence = section("gitlab", "tool-results", "tool evidence");
        var options = new AnalysisAiOptions("gpt-5.4", "high");
        var request = new AnalysisAiChatRequest(
                "corr-123",
                "zt01",
                "release/2026.04",
                "sample/runtime",
                List.of(deterministicEvidence),
                List.of(toolEvidence),
                null,
                List.of(),
                "What changed?",
                options
        );

        var artifactRequest = factory.create(request);

        assertEquals("corr-123", artifactRequest.correlationId());
        assertEquals("zt01", artifactRequest.environment());
        assertEquals("release/2026.04", artifactRequest.gitLabBranch());
        assertEquals("sample/runtime", artifactRequest.gitLabGroup());
        assertEquals(List.of(deterministicEvidence, toolEvidence), artifactRequest.evidenceSections());
        assertEquals(options, artifactRequest.options());
    }

    private AnalysisEvidenceSection section(String provider, String category, String title) {
        return new AnalysisEvidenceSection(
                provider,
                category,
                List.of(new AnalysisEvidenceItem(title, List.of()))
        );
    }
}
