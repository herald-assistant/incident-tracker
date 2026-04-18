package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequest;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlabmcp.GitLabMcpTools;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkPreparationServiceTest {

    @TempDir
    Path tempDirectory;

    private final CopilotSdkToolBridge toolBridge = new CopilotSdkToolBridge(
            List.of(
                    MethodToolCallbackProvider.builder()
                            .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                            .build()
            ),
            new ObjectMapper()
    );

    @Test
    void shouldPrepareSdkObjectsFromAnalysisRequest() {
        var properties = new CopilotSdkProperties();
        properties.setCliPath("C:\\tools\\copilot.exe");
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        properties.setClientName("incidenttracker-test");
        properties.setSkillResourceRoots(List.of("copilot/skills"));
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var service = new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties)
        );
        var request = new AnalysisAiAnalysisRequest(
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

        var prepared = service.prepare(request);

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

        assertTrue(prepared.messageOptions().getPrompt().contains("correlationId: timeout-123"));
        assertTrue(prepared.messageOptions().getPrompt().contains("environment: dev3"));
        assertTrue(prepared.messageOptions().getPrompt().contains("gitLabBranch: release/2026.04"));
        assertTrue(prepared.messageOptions().getPrompt().contains("gitLabGroup: sample/runtime"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Available tools:"));
        assertTrue(prepared.messageOptions().getPrompt().contains("gitlab_read_repository_file_chunk"));
        assertTrue(prepared.messageOptions().getPrompt().contains("enterprise software incident analysis"));
        assertTrue(prepared.messageOptions().getPrompt().contains("analyst, tester, or junior/mid developer"));
        assertTrue(prepared.messageOptions().getPrompt().contains("The available evidence comes only from our system"));
        assertTrue(prepared.messageOptions().getPrompt().contains("If another team, admins, integration owners, or DB specialists are likely needed"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Follow any loaded skills that are relevant for incident analysis and tool usage."));
        assertTrue(prepared.messageOptions().getPrompt().contains("keep the provided gitLabGroup and gitLabBranch unchanged"));
        assertTrue(prepared.messageOptions().getPrompt().contains("No Dynatrace tools are available during the session."));
        assertTrue(prepared.messageOptions().getPrompt().contains("Return the analysis in Polish."));
        assertTrue(prepared.messageOptions().getPrompt().contains("write every field value in concise, professional Polish"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Use valid Markdown in summary, recommendedAction, and rationale."));
        assertTrue(prepared.messageOptions().getPrompt().contains("Never use pipe separators like \"|\" to join multiple points in one line."));
        assertTrue(prepared.messageOptions().getPrompt().contains("Use **bold** for the most important facts and `code spans` for technical identifiers"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Return exactly these lines:"));
        assertTrue(prepared.messageOptions().getPrompt().contains("recommendedAction: <2-4 concise markdown bullet lines"));
        assertTrue(prepared.messageOptions().getPrompt().contains("rationale: <3-6 concise markdown bullet lines"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Provider: elasticsearch, category: logs"));
        assertTrue(prepared.messageOptions().getPrompt().contains("- billing-service log entry | level=ERROR | serviceName=billing-service | message=Read timed out while calling catalog-service"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Provider: dynatrace, category: runtime-signals"));
        assertTrue(prepared.messageOptions().getPrompt().contains("metricLabel=service.response.time.p95 | maxValue=8.67 | unit=ms"));
        assertTrue(prepared.messageOptions().getPrompt().contains("Provider: gitlab, category: code-changes"));
        assertEquals(prepared.messageOptions().getPrompt(), prepared.prompt());
    }

    @Test
    void shouldUsePatWhenGithubTokenIsProvided() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setGithubToken("ghp_test_token");
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var service = new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties)
        );
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        var prepared = service.prepare(request);

        assertEquals("ghp_test_token", prepared.clientOptions().getGithubToken());
        assertEquals(Boolean.FALSE, prepared.clientOptions().getUseLoggedInUser());
    }

    @Test
    void shouldFallbackToLoggedInUserWhenGithubTokenIsMissing() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var service = new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties)
        );
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        var prepared = service.prepare(request);

        assertEquals("incidenttracker", prepared.sessionConfig().getClientName());
        assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.sessionConfig().getWorkingDirectory());
        assertEquals(Boolean.TRUE, prepared.clientOptions().getUseLoggedInUser());
        assertEquals(null, prepared.clientOptions().getGithubToken());
        assertEquals(null, prepared.sessionConfig().getModel());
        assertEquals(null, prepared.sessionConfig().getReasoningEffort());
        assertEquals(1, prepared.sessionConfig().getSkillDirectories().size());
    }

    @Test
    void shouldPassDisabledSkillsToSessionConfig() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setDisabledSkills(List.of("incident-analysis-gitlab-tools"));
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var service = new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties)
        );
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        var prepared = service.prepare(request);

        assertEquals(List.of("incident-analysis-gitlab-tools"), prepared.sessionConfig().getDisabledSkills());
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var service = new CopilotSdkPreparationService(
                properties,
                toolBridge,
                new CopilotSkillRuntimeLoader(properties)
        );
        var request = new AnalysisAiAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "sample/runtime",
                List.of()
        );

        var prepared = service.prepare(request);
        var decision = prepared.sessionConfig()
                .getOnPermissionRequest()
                .handle(new PermissionRequest(), null)
                .join();

        assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
    }

}

