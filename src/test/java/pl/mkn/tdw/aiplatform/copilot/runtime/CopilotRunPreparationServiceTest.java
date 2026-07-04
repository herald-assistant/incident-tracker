package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import pl.mkn.tdw.testsupport.copilot.CopilotSessionConfigFactoryTestCreator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CopilotRunPreparationServiceTest {

    @Test
    void shouldPrepareSessionFromNeutralRunRequest() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        Consumer<AnalysisEvidenceSection> evidenceSink = section -> {
        };
        var service = new CopilotRunPreparationService(
                new CopilotPreparedSessionFactory(CopilotSessionConfigFactoryTestCreator.create(properties))
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
        ).withInitialReport(report());

        try (var prepared = service.prepare(runRequest)) {
            assertEquals("run-123", prepared.runReference());
            assertEquals(CopilotSessionTarget.Type.NEW, prepared.sessionTarget().type());
            assertEquals("Prompt body", prepared.prompt());
            assertEquals("Prompt body", prepared.messageOptions().getPrompt());
            assertEquals(Map.of("artifact.md", "# Artifact"), prepared.artifactContents());
            assertSame(evidenceSink, prepared.evidenceSink());
            assertEquals(report(), prepared.initialReport());
            assertEquals("session-123", prepared.sessionConfig().getSessionId());
            assertNotNull(prepared.resumeSessionConfig());
            assertEquals("gpt-5.4", prepared.sessionConfig().getModel());
            assertEquals("medium", prepared.sessionConfig().getReasoningEffort());
            assertEquals(List.of("tool-a", "skill"), prepared.sessionConfig().getAvailableTools());
            assertEquals(List.of("copilot-skills/incident"), prepared.sessionConfig().getSkillDirectories());
        }
    }

    @Test
    void shouldPrepareExistingSessionTargetForResume() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        Consumer<AnalysisEvidenceSection> evidenceSink = section -> {
        };
        var service = new CopilotRunPreparationService(
                new CopilotPreparedSessionFactory(CopilotSessionConfigFactoryTestCreator.create(properties))
        );
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "session-456",
                List.of(),
                List.of("tool-a"),
                List.of("copilot-skills/incident"),
                new CopilotModelSelection("gpt-5.4", "medium"),
                "Denied"
        );
        var runRequest = new CopilotRunRequest(
                "run-456",
                CopilotRunAuth.localToken(),
                CopilotSessionTarget.existing("sdk-session-456"),
                "Next question",
                sessionConfigRequest,
                Map.of(),
                evidenceSink
        );

        try (var prepared = service.prepare(runRequest)) {
            assertEquals(CopilotSessionTarget.Type.EXISTING, prepared.sessionTarget().type());
            assertEquals("sdk-session-456", prepared.sessionTarget().sessionId());
            assertEquals("Next question", prepared.prompt());
            assertEquals("Next question", prepared.messageOptions().getPrompt());
            assertSame(evidenceSink, prepared.evidenceSink());
            assertNotNull(prepared.resumeSessionConfig());
            assertEquals(List.of("tool-a", "skill"), prepared.resumeSessionConfig().getAvailableTools());
        }
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "report-1",
                "Header",
                "Sub header",
                "Summary",
                List.of(),
                AnalysisReportMeta.empty()
        );
    }
}
