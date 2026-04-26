package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotSdkPreparationServiceEvidenceReferencePromptTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldPromptForDigestVerificationAndEvidenceReferences() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var bridge = mock(CopilotSdkToolBridge.class);
        when(bridge.buildToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(List.<ToolDefinition>of());
        var service = new CopilotSdkPreparationService(
                properties,
                bridge,
                new CopilotSkillRuntimeLoader(properties),
                new CopilotArtifactService(new ObjectMapper()),
                new CopilotSessionMetricsRegistry(new CopilotMetricsProperties())
        );

        var prompt = service.preparePrompt(request());

        assertTrue(prompt.contains("Read `00-incident-manifest.json` first and use it as the artifact index, then read `01-incident-digest.md`."));
        assertTrue(prompt.contains("Use raw evidence artifacts to verify the digest before making a claim."));
        assertTrue(prompt.contains("When possible, include evidenceReferences with artifactId and itemId for important claims."));
        assertTrue(prompt.contains("Prefer `evidenceReferences` for important claims and use the artifact display name as `artifactId`."));
        assertTrue(prompt.contains("Use stable `itemId` values from the evidence artifacts whenever a claim is grounded in a specific evidence item."));
        assertTrue(prompt.contains("01-incident-digest.md"));
        assertTrue(prompt.contains("## itemId: elastic-logs-001"));
    }

    private AnalysisAiAnalysisRequest request() {
        return new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "sample/runtime",
                List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(new AnalysisEvidenceItem(
                                "error log",
                                List.of(new AnalysisEvidenceAttribute("message", "timeout"))
                        ))
                ))
        );
    }
}
