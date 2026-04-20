package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.Attachment;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequest;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotAttachmentArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabMcpTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkPreparationServiceTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopilotSdkToolBridge toolBridge = new CopilotSdkToolBridge(
            List.of(
                    MethodToolCallbackProvider.builder()
                            .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                            .build()
            ),
            objectMapper
    );

    @Test
    void shouldPrepareSdkObjectsFromAnalysisRequest() throws Exception {
        var properties = baseProperties();
        properties.setCliPath("C:\\tools\\copilot.exe");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        properties.setClientName("incidenttracker-test");
        properties.setSkillResourceRoots(List.of("copilot/skills"));

        var service = createService(properties);
        var request = sampleRequest();

        try (var prepared = service.prepare(request)) {
            assertEquals("C:\\tools\\copilot.exe", prepared.clientOptions().getCliPath());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.clientOptions().getCwd());
            assertEquals(Boolean.TRUE, prepared.clientOptions().getUseLoggedInUser());
            assertEquals(null, prepared.clientOptions().getGithubToken());

            assertEquals("incidenttracker-test", prepared.sessionConfig().getClientName());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.sessionConfig().getWorkingDirectory());
            assertEquals("gpt-5.4", prepared.sessionConfig().getModel());
            assertEquals("medium", prepared.sessionConfig().getReasoningEffort());
            assertFalse(prepared.sessionConfig().isStreaming());
            assertEquals(3, prepared.sessionConfig().getTools().size());
            assertEquals(1, prepared.sessionConfig().getSkillDirectories().size());
            assertTrue(prepared.sessionConfig().getSkillDirectories().get(0).contains("copilot_skills"));
            assertEquals(PermissionHandler.APPROVE_ALL, prepared.sessionConfig().getOnPermissionRequest());
            assertEquals(List.of(), prepared.sessionConfig().getDisabledSkills());

            var prompt = prepared.messageOptions().getPrompt();
            assertTrue(prompt.contains("correlationId: timeout-123"));
            assertTrue(prompt.contains("environment: dev3"));
            assertTrue(prompt.contains("gitLabBranch: release/2026.04"));
            assertTrue(prompt.contains("gitLabGroup: sample/runtime"));
            assertTrue(prompt.contains("Analyze the attached artifacts as the primary source of truth."));
            assertTrue(prompt.contains("Read `00-incident-manifest.json` first"));
            assertTrue(prompt.contains("Attached artifacts:"));
            assertTrue(prompt.contains("00-incident-manifest.json"));
            assertTrue(prompt.contains("01-elasticsearch-logs.json"));
            assertTrue(prompt.contains("02-dynatrace-runtime-signals.json"));
            assertTrue(prompt.contains("03-gitlab-code-changes.json"));
            assertTrue(prompt.contains("Available tools:"));
            assertTrue(prompt.contains("gitlab_read_repository_file_chunk"));
            assertTrue(prompt.contains("enterprise software incident analysis"));
            assertTrue(prompt.contains("analyst, tester, or junior/mid developer"));
            assertTrue(prompt.contains("may be new to the affected area"));
            assertTrue(prompt.contains("The available evidence comes only from our system"));
            assertTrue(prompt.contains("If another team, admins, integration owners, or DB specialists are likely needed"));
            assertTrue(prompt.contains("Follow any loaded skills that are relevant for incident analysis and tool usage."));
            assertTrue(prompt.contains("keep the provided gitLabGroup and gitLabBranch unchanged"));
            assertTrue(prompt.contains("No Dynatrace tools are available during the session."));
            assertTrue(prompt.contains("do not stop at the single failing line"));
            assertTrue(prompt.contains("broader request or business flow"));
            assertTrue(prompt.contains("Return the analysis in Polish."));
            assertTrue(prompt.contains("write every field value in concise, professional Polish"));
            assertTrue(prompt.contains("Use valid Markdown in summary, affectedFunction, recommendedAction, and rationale."));
            assertTrue(prompt.contains("Never use pipe separators like \"|\" to join multiple points in one line."));
            assertTrue(prompt.contains("Use **bold** for the most important facts and `code spans` for technical identifiers"));
            assertTrue(prompt.contains("Return exactly these lines:"));
            assertTrue(prompt.contains("summary: <a short markdown block in Polish: one concise opening sentence and then 2-5 markdown bullet lines"));
            assertTrue(prompt.contains("recommendedAction: <2-4 concise markdown bullet lines"));
            assertTrue(prompt.contains("rationale: <3-6 concise markdown bullet lines"));
            assertTrue(prompt.contains("affectedFunction: <a short markdown block in Polish based on the broader GitLab exploration"));
            assertFalse(prompt.contains("Read timed out while calling catalog-service"));
            assertFalse(prompt.contains("metricLabel=service.response.time.p95"));
            assertEquals(prompt, prepared.prompt());

            assertEquals(4, prepared.messageOptions().getAttachments().size());
            var manifestAttachment = (Attachment) prepared.messageOptions().getAttachments().get(0);
            assertEquals("00-incident-manifest.json", manifestAttachment.displayName());
            var manifestContent = Files.readString(Path.of(manifestAttachment.path()));
            assertTrue(manifestContent.contains("\"readFirst\""));
            assertTrue(manifestContent.contains("\"00-incident-manifest.json\""));
            assertTrue(manifestContent.contains("\"01-elasticsearch-logs.json\""));

            var logsAttachment = (Attachment) prepared.messageOptions().getAttachments().get(1);
            assertEquals("01-elasticsearch-logs.json", logsAttachment.displayName());
            var logsContent = Files.readString(Path.of(logsAttachment.path()));
            assertTrue(logsContent.contains("\"provider\""));
            assertTrue(logsContent.contains("\"elasticsearch\""));
            assertTrue(logsContent.contains("\"Read timed out while calling catalog-service\""));
        }
    }

    @Test
    void shouldUsePatWhenGithubTokenIsProvided() {
        var properties = baseProperties();
        properties.setGithubToken("ghp_test_token");

        var service = createService(properties);
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals("ghp_test_token", prepared.clientOptions().getGithubToken());
            assertEquals(Boolean.FALSE, prepared.clientOptions().getUseLoggedInUser());
        }
    }

    @Test
    void shouldFallbackToLoggedInUserWhenGithubTokenIsMissing() {
        var properties = baseProperties();
        var service = createService(properties);
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals("incidenttracker", prepared.sessionConfig().getClientName());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.sessionConfig().getWorkingDirectory());
            assertEquals(Boolean.TRUE, prepared.clientOptions().getUseLoggedInUser());
            assertEquals(null, prepared.clientOptions().getGithubToken());
            assertEquals(null, prepared.sessionConfig().getModel());
            assertEquals(null, prepared.sessionConfig().getReasoningEffort());
            assertEquals(1, prepared.sessionConfig().getSkillDirectories().size());
        }
    }

    @Test
    void shouldPassDisabledSkillsToSessionConfig() {
        var properties = baseProperties();
        properties.setDisabledSkills(List.of("incident-analysis-gitlab-tools"));

        var service = createService(properties);
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of("incident-analysis-gitlab-tools"), prepared.sessionConfig().getDisabledSkills());
        }
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = baseProperties();
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);

        var service = createService(properties);
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            var decision = prepared.sessionConfig()
                    .getOnPermissionRequest()
                    .handle(new PermissionRequest(), null)
                    .join();

            assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
        }
    }

    private CopilotSdkPreparationService createService(CopilotSdkProperties properties) {
        return new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties),
                new CopilotAttachmentArtifactService(properties, objectMapper)
        );
    }

    private CopilotSdkProperties baseProperties() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        properties.setAttachmentArtifactDirectory(tempDirectory.resolve("attachments").toString());
        return properties;
    }

    private AnalysisAiAnalysisRequest sampleRequest() {
        return new AnalysisAiAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(new AnalysisEvidenceItem(
                                "billing-service log entry",
                                List.of(
                                        new AnalysisEvidenceAttribute("level", "ERROR"),
                                        new AnalysisEvidenceAttribute("serviceName", "billing-service"),
                                        new AnalysisEvidenceAttribute("message", "Read timed out while calling catalog-service")
                                )
                        ))
                ), new AnalysisEvidenceSection(
                        "dynatrace",
                        "runtime-signals",
                        List.of(new AnalysisEvidenceItem(
                                "Dynatrace metric service.response.time.p95 for case-evaluation-service",
                                List.of(
                                        new AnalysisEvidenceAttribute("metricLabel", "service.response.time.p95"),
                                        new AnalysisEvidenceAttribute("maxValue", "8.67"),
                                        new AnalysisEvidenceAttribute("unit", "ms")
                                )
                        ))
                ), new AnalysisEvidenceSection(
                        "gitlab",
                        "code-changes",
                        List.of(new AnalysisEvidenceItem(
                                "edge-client-service change hint",
                                List.of(
                                        new AnalysisEvidenceAttribute("projectName", "edge-client-service"),
                                        new AnalysisEvidenceAttribute("filePath", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java"),
                                        new AnalysisEvidenceAttribute("summary", "HTTP client timeout defaults were changed.")
                                )
                        ))
                ))
        );
    }
}
