package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CopilotRunPreparationServiceTest {

    @Test
    void shouldPrepareSessionFromNeutralRunRequest() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        Consumer<AnalysisEvidenceSection> evidenceSink = section -> {
        };
        var service = new CopilotRunPreparationService(
                new CopilotPreparedSessionFactory(new CopilotSessionConfigFactory(properties))
        );
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "session-123",
                List.of(),
                List.of("tool-a"),
                List.of("copilot-skills/incident"),
                new CopilotModelSelection("gpt-5.4", "medium"),
                "Denied"
        );
        var runRequest = new CopilotRunRequest(
                "run-123",
                "Prompt body",
                sessionConfigRequest,
                Map.of("artifact.md", "# Artifact"),
                evidenceSink
        );

        try (var prepared = service.prepare(runRequest)) {
            assertEquals("run-123", prepared.runReference());
            assertEquals("Prompt body", prepared.prompt());
            assertEquals("Prompt body", prepared.messageOptions().getPrompt());
            assertEquals(Map.of("artifact.md", "# Artifact"), prepared.artifactContents());
            assertSame(evidenceSink, prepared.evidenceSink());
            assertEquals("session-123", prepared.sessionConfig().getSessionId());
            assertEquals("gpt-5.4", prepared.sessionConfig().getModel());
            assertEquals("medium", prepared.sessionConfig().getReasoningEffort());
            assertEquals(List.of("tool-a"), prepared.sessionConfig().getAvailableTools());
            assertEquals(List.of("copilot-skills/incident"), prepared.sessionConfig().getSkillDirectories());
        }
    }
}
