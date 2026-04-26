package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.PreToolUseHookInput;
import com.github.copilot.sdk.json.ToolDefinition;
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
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotPromptRenderer;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkPreparationService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSessionConfigFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotToolAccessPolicyFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabMcpTools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.artifactService;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolBridge;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotSdkPreparationServiceTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopilotSdkToolBridge toolBridge = toolBridge(
            List.of(
                    MethodToolCallbackProvider.builder()
                            .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                            .build()
            ),
            objectMapper,
            toolEvidenceCaptureRegistry(objectMapper),
            metricsRegistry(),
            metricsLogger()
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
            assertNotNull(prepared.sessionConfig().getSessionId());
            assertTrue(prepared.sessionConfig().getSessionId().startsWith("analysis-"));
            assertFalse(prepared.sessionConfig().isStreaming());
            assertEquals(
                    Set.of(
                            "gitlab_find_class_references",
                            "gitlab_find_flow_context",
                            "gitlab_read_repository_file_chunk",
                            "gitlab_read_repository_file_chunks",
                            "gitlab_read_repository_file_outline"
                    ),
                    Set.copyOf(prepared.sessionConfig().getAvailableTools())
            );
            assertEquals(5, prepared.sessionConfig().getTools().size());
            assertEquals(1, prepared.sessionConfig().getSkillDirectories().size());
            assertTrue(prepared.sessionConfig().getSkillDirectories().get(0).contains("copilot_skills"));
            assertEquals(PermissionHandler.APPROVE_ALL, prepared.sessionConfig().getOnPermissionRequest());
            assertEquals(List.of(), prepared.sessionConfig().getDisabledSkills());
            assertNotNull(prepared.sessionConfig().getHooks());

            var prompt = prepared.messageOptions().getPrompt();
            assertTrue(prompt.contains("correlationId: timeout-123"));
            assertTrue(prompt.contains("environment: dev3"));
            assertTrue(prompt.contains("gitLabBranch: release/2026.04"));
            assertTrue(prompt.contains("gitLabGroup: sample/runtime"));
            assertTrue(prompt.contains("Analyze the incident artifacts as the primary source of truth."));
            assertTrue(prompt.contains("Read `00-incident-manifest.json` first"));
            assertTrue(prompt.contains("then read `01-incident-digest.md`"));
            assertTrue(prompt.contains("Use raw evidence artifacts to verify the digest before making a claim."));
            assertTrue(prompt.contains("Incident artifacts are embedded directly in this prompt. Do not try to open them from the local filesystem."));
            assertTrue(prompt.contains("The authoritative artifact contents are embedded below in this prompt."));
            assertTrue(prompt.contains("Do not claim that you only see the artifact list when the artifact contents are embedded below."));
            assertTrue(prompt.contains("Artifacts:"));
            assertTrue(prompt.contains("Embedded artifact contents:"));
            assertTrue(prompt.contains("00-incident-manifest.json"));
            assertTrue(prompt.contains("01-incident-digest.md"));
            assertTrue(prompt.contains("02-elasticsearch-logs.md"));
            assertTrue(prompt.contains("03-dynatrace-runtime-signals.md"));
            assertTrue(prompt.contains("04-gitlab-resolved-code.md"));
            assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 00-incident-manifest.json | mimeType=application/json>>>"));
            assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 01-incident-digest.md | mimeType=text/markdown>>>"));
            assertTrue(prompt.contains("<<<BEGIN ARTIFACT: 02-elasticsearch-logs.md | mimeType=text/markdown>>>"));
            assertTrue(prompt.contains("\"deliveryMode\" : \"embedded-prompt\""));
            assertTrue(prompt.contains("Read timed out while calling catalog-service"));
            assertTrue(prompt.contains("Problem `P-26042756` `Gateway timeout on backend`."));
            assertTrue(prompt.contains("GitLab resolved code references"));
            assertTrue(prompt.contains("Available capability groups:"));
            assertTrue(prompt.contains("GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow, affected-function or DB code-grounding gaps."));
            assertTrue(prompt.contains("enterprise software incident analysis"));
            assertTrue(prompt.contains("operator, tester, analyst, or junior/mid developer"));
            assertTrue(prompt.contains("may not know the affected system area"));
            assertTrue(prompt.contains("Hard rules:"));
            assertTrue(prompt.contains("Treat environment, gitLabBranch and gitLabGroup as fixed session context."));
            assertTrue(prompt.contains("Only the explicitly listed capability groups are enabled for this session."));
            assertTrue(prompt.contains("Local workspace, filesystem and shell or terminal tools are blocked. Do not inspect the local disk."));
            assertTrue(prompt.contains("Do not invent environment, branch, group, project, table, owner, process, bounded context, or downstream system."));
            assertTrue(prompt.contains("Follow loaded skills for incident analysis, GitLab exploration, DB/data diagnostics and handoff quality."));
            assertTrue(prompt.contains("Use tools only for evidence gaps listed in `evidenceCoverage.gaps`"));
            assertTrue(prompt.contains("Do not use tools just because they are available."));
            assertTrue(prompt.contains("GitLab, Elasticsearch and Database tools are coverage-aware"));
            assertTrue(prompt.contains("If the incident artifacts already contain enough evidence but GitLab tools are enabled, still use a focused GitLab search/read to improve `affectedFunction`; if GitLab tools are not enabled, answer directly from artifacts."));
            assertTrue(prompt.contains("If the likely technical error is clear but the affected function or broader flow is not understandable for a beginner analyst, use GitLab tools to read enough surrounding code to explain the flow and handoff."));
            assertTrue(prompt.contains("Write `affectedFunction` in non-code, operator-friendly technical/functional language"));
            assertTrue(prompt.contains("Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom"));
            assertTrue(prompt.contains("Use deterministic GitLab evidence or enabled GitLab tools to identify the entity, repository predicate, likely table/column names and direct relations that should guide DB diagnostics."));
            assertTrue(prompt.contains("If an exception, stacktrace or deterministic code evidence grounds a class name, use GitLab class-reference or flow tools with grounded class names and focused keywords before broad DB discovery"));
            assertTrue(prompt.contains("Every GitLab tool call must include `reason`: one short Polish sentence"));
            assertTrue(prompt.contains("Every Database tool call must include `reason`: one short Polish sentence"));
            assertTrue(prompt.contains("When possible, include evidenceReferences with artifactId and itemId for important claims."));
            assertTrue(prompt.contains("If visibility is incomplete, state exactly what remains unverified and what the next verification step is."));
            assertTrue(prompt.contains("Return the analysis in Polish."));
            assertTrue(prompt.contains("Return only valid JSON."));
            assertTrue(prompt.contains("Do not wrap it in Markdown."));
            assertTrue(prompt.contains("Do not add prose before or after JSON."));
            assertTrue(prompt.contains("Use concise professional Markdown in string values where the schema says markdown string."));
            assertTrue(prompt.contains("Use `code spans` for technical identifiers"));
            assertTrue(prompt.contains("Never join multiple points with pipe separators like \"|\"."));
            assertTrue(prompt.contains("Required schema:"));
            assertTrue(prompt.contains("\"detectedProblem\": \"string\""));
            assertTrue(prompt.contains("\"summary\": \"markdown string in Polish\""));
            assertTrue(prompt.contains("\"recommendedAction\": \"markdown string in Polish\""));
            assertTrue(prompt.contains("\"affectedFunction\": \"markdown string in Polish\""));
            assertTrue(prompt.contains("\"confidence\": \"high|medium|low\""));
            assertTrue(prompt.contains("\"evidenceReferences\": ["));
            assertTrue(prompt.contains("\"visibilityLimits\": [\"string\"]"));
            assertTrue(prompt.contains("Prefer `evidenceReferences` for important claims"));
            assertTrue(prompt.contains("Use stable `itemId` values from the evidence artifacts"));
            assertFalse(prompt.contains("metricLabel=service.response.time.p95"));
            assertEquals(prompt, prepared.prompt());

            assertTrue(prepared.messageOptions().getAttachments() == null || prepared.messageOptions().getAttachments().isEmpty());
            assertEquals(5, prepared.artifactContents().size());
            var manifestContent = prepared.artifactContents().get("00-incident-manifest.json");
            assertTrue(manifestContent.contains("\"readFirst\""));
            assertTrue(manifestContent.contains("\"readNext\""));
            assertTrue(manifestContent.contains("\"artifactFormatVersion\" : \"copilot-artifacts-v2\""));
            assertTrue(manifestContent.contains("\"00-incident-manifest.json\""));
            assertTrue(manifestContent.contains("\"01-incident-digest.md\""));
            assertTrue(manifestContent.contains("\"02-elasticsearch-logs.md\""));
            assertTrue(manifestContent.contains("\"03-dynatrace-runtime-signals.md\""));
            assertTrue(manifestContent.contains("\"04-gitlab-resolved-code.md\""));
            assertTrue(manifestContent.contains("\"elastic-logs-001\""));
            assertTrue(manifestContent.contains("\"gitlab-resolved-code-001\""));
            assertTrue(manifestContent.contains("\"toolPolicy\""));
            assertTrue(manifestContent.contains("\"localWorkspaceAccessBlocked\" : true"));
            assertTrue(manifestContent.contains("\"enabledToolNames\""));
            assertTrue(manifestContent.contains("\"gitlab_find_flow_context\""));
            assertTrue(manifestContent.contains("\"evidenceCoverage\""));
            assertTrue(manifestContent.contains("\"gitLab\" : \"DIRECT_COLLABORATOR_ATTACHED\""));
            assertTrue(manifestContent.contains("\"code\" : \"MISSING_FLOW_CONTEXT\""));
            assertTrue(manifestContent.contains("\"code\" : \"AFFECTED_FUNCTION_GITLAB_RECOMMENDED\""));
            assertTrue(manifestContent.contains("\"deliveryMode\" : \"embedded-prompt\""));
            assertTrue(manifestContent.contains("\"artifactsArePrimarySourceOfTruth\" : true"));

            var digestContent = prepared.artifactContents().get("01-incident-digest.md");
            assertTrue(digestContent.contains("# Incident digest"));
            assertTrue(digestContent.contains("- correlationId: `timeout-123`"));
            assertTrue(digestContent.contains("- GitLab: `DIRECT_COLLABORATOR_ATTACHED`"));
            assertTrue(digestContent.contains("## Known evidence gaps"));

            var logsContent = prepared.artifactContents().get("02-elasticsearch-logs.md");
            assertTrue(logsContent.contains("Elasticsearch log evidence"));
            assertTrue(logsContent.contains("## itemId: elastic-logs-001"));
            assertTrue(logsContent.contains("Log entry `1` `ERROR` `billing-service`"));
            assertTrue(logsContent.contains("- message:"));
            assertTrue(logsContent.contains("Read timed out while calling catalog-service"));
            assertFalse(logsContent.contains("\"provider\""));

            var dynatraceContent = prepared.artifactContents().get("03-dynatrace-runtime-signals.md");
            assertTrue(dynatraceContent.contains("Dynatrace runtime signals"));
            assertTrue(dynatraceContent.contains("## itemId: dynatrace-runtime-signals-001"));
            assertTrue(dynatraceContent.contains("- collection status: COLLECTED"));
            assertTrue(dynatraceContent.contains("component `case-evaluation-service`: MATCHED, SIGNALS_PRESENT."));
            assertTrue(dynatraceContent.contains("Problem `P-26042756` `Gateway timeout on backend`."));
            assertFalse(dynatraceContent.contains("\"metricLabel\""));

            var gitLabContent = prepared.artifactContents().get("04-gitlab-resolved-code.md");
            assertTrue(gitLabContent.contains("GitLab resolved code references"));
            assertTrue(gitLabContent.contains("## itemId: gitlab-resolved-code-001"));
            assertTrue(gitLabContent.contains("- repository: `edge-client-service`"));
            assertTrue(gitLabContent.contains("- returned lines: `5-12` of `14`"));
            assertTrue(gitLabContent.contains("```java"));
            assertFalse(gitLabContent.contains("\"content\""));

            var deniedToolDecision = prepared.sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                    .join();
            assertEquals("deny", deniedToolDecision.permissionDecision());
        }
    }

    @Test
    void shouldBuildSessionBoundToolContextForBridgeAndSessionConfig() {
        var properties = baseProperties();
        properties.setSkillResourceRoots(List.of("copilot/skills"));
        var request = requestWithoutToolCoveredEvidence();
        var bridge = mock(CopilotSdkToolBridge.class);
        var expectedTools = List.of(ToolDefinition.createSkipPermission(
                "gitlab_read_repository_file",
                "Read repository file",
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        ));
        when(bridge.buildToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(expectedTools);

        var service = new CopilotSdkPreparationService(
                bridge,
                new CopilotSkillRuntimeLoader(properties),
                artifactService(objectMapper),
                policyFactory(),
                promptRenderer(),
                sessionConfigFactory(properties),
                metricsRegistry()
        );

        try (var prepared = service.prepare(request)) {
            verify(bridge).buildToolDefinitions(org.mockito.ArgumentMatchers.argThat(context ->
                    "timeout-123".equals(context.correlationId())
                            && "dev3".equals(context.environment())
                            && "release/2026.04".equals(context.gitLabBranch())
                            && "sample/runtime".equals(context.gitLabGroup())
                            && context.analysisRunId() != null
                            && !context.analysisRunId().isBlank()
                            && context.copilotSessionId() != null
                            && context.copilotSessionId().startsWith("analysis-")
            ));
            assertEquals(expectedTools, prepared.sessionConfig().getTools());
            assertEquals(List.of("gitlab_read_repository_file"), prepared.sessionConfig().getAvailableTools());
            var allowedToolDecision = prepared.sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("gitlab_read_repository_file"), null)
                    .join();
            assertEquals("allow", allowedToolDecision.permissionDecision());
            var deniedToolDecision = prepared.sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                    .join();
            assertEquals("deny", deniedToolDecision.permissionDecision());
            assertNotNull(prepared.sessionConfig().getSessionId());
            assertTrue(prepared.sessionConfig().getSessionId().startsWith("analysis-"));
        }
    }

    @Test
    void shouldAllowOnlyExplicitSpringToolsWhenArtifactsDoNotAlreadyCoverThem() {
        var properties = baseProperties();
        var request = requestWithoutToolCoveredEvidence();
        var bridge = mock(CopilotSdkToolBridge.class);
        var expectedTools = List.of(
                ToolDefinition.createSkipPermission(
                        "gitlab_read_repository_file",
                        "Read repository file",
                        Map.of("type", "object", "properties", Map.of()),
                        invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
                ),
                ToolDefinition.createSkipPermission(
                        "db_get_scope",
                        "Get DB scope",
                        Map.of("type", "object", "properties", Map.of()),
                        invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
                )
        );
        when(bridge.buildToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(expectedTools);

        var service = new CopilotSdkPreparationService(
                bridge,
                new CopilotSkillRuntimeLoader(properties),
                artifactService(objectMapper),
                policyFactory(),
                promptRenderer(),
                sessionConfigFactory(properties),
                metricsRegistry()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of(expectedTools.get(0)), prepared.sessionConfig().getTools());
            assertEquals(
                    Set.of("gitlab_read_repository_file"),
                    Set.copyOf(prepared.sessionConfig().getAvailableTools())
            );
        }
    }

    @Test
    void shouldFilterRegisteredToolsWhenCoverageDoesNotAllowTheirSpecificUse() {
        var properties = baseProperties();
        var request = sampleRequest();
        var bridge = mock(CopilotSdkToolBridge.class);
        var elasticTool = ToolDefinition.createSkipPermission(
                "elastic_search_logs_by_correlation_id",
                "Search logs",
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
        var gitLabTool = ToolDefinition.createSkipPermission(
                "gitlab_read_repository_file",
                "Read repository file",
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
        var dbTool = ToolDefinition.createSkipPermission(
                "db_get_scope",
                "Get DB scope",
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
        when(bridge.buildToolDefinitions(any(CopilotToolSessionContext.class)))
                .thenReturn(List.of(elasticTool, gitLabTool, dbTool));

        var service = new CopilotSdkPreparationService(
                bridge,
                new CopilotSkillRuntimeLoader(properties),
                artifactService(objectMapper),
                policyFactory(),
                promptRenderer(),
                sessionConfigFactory(properties),
                metricsRegistry()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of(), prepared.sessionConfig().getTools());
            assertEquals(List.of(), prepared.sessionConfig().getAvailableTools());
            assertFalse(prepared.prompt().contains("Database diagnostics:"));
            assertFalse(prepared.prompt().contains("Elasticsearch logs: fetch additional logs"));
            assertFalse(prepared.prompt().contains("GitLab code: inspect class references/imports"));
            var deniedElasticDecision = prepared.sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("elastic_search_logs_by_correlation_id"), null)
                    .join();
            assertEquals("deny", deniedElasticDecision.permissionDecision());
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
                toolBridge,
                new CopilotSkillRuntimeLoader(properties),
                artifactService(objectMapper),
                policyFactory(),
                promptRenderer(),
                sessionConfigFactory(properties),
                metricsRegistry()
        );
    }

    private CopilotToolAccessPolicyFactory policyFactory() {
        return new CopilotToolAccessPolicyFactory(new CopilotEvidenceCoverageEvaluator());
    }

    private CopilotPromptRenderer promptRenderer() {
        return new CopilotPromptRenderer();
    }

    private CopilotSessionConfigFactory sessionConfigFactory(CopilotSdkProperties properties) {
        return new CopilotSessionConfigFactory(properties);
    }

    private CopilotSessionMetricsRegistry metricsRegistry() {
        return new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
    }

    private CopilotMetricsLogger metricsLogger() {
        return new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper);
    }

    private CopilotSdkProperties baseProperties() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
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
                                        new AnalysisEvidenceAttribute("timestamp", "2026-04-11T20:57:33.285Z"),
                                        new AnalysisEvidenceAttribute("level", "ERROR"),
                                        new AnalysisEvidenceAttribute("serviceName", "billing-service"),
                                        new AnalysisEvidenceAttribute("className", "com.example.synthetic.BillingService"),
                                        new AnalysisEvidenceAttribute("message", "Read timed out while calling catalog-service")
                                )
                        ))
                ), new AnalysisEvidenceSection(
                        "dynatrace",
                        "runtime-signals",
                        List.of(
                                new AnalysisEvidenceItem(
                                        "Dynatrace collection status",
                                        List.of(
                                                new AnalysisEvidenceAttribute("dynatraceItemType", "collection-status"),
                                                new AnalysisEvidenceAttribute("collectionStatus", "COLLECTED"),
                                                new AnalysisEvidenceAttribute("collectionReason", "Dynatrace query completed successfully."),
                                                new AnalysisEvidenceAttribute("correlationStatus", "MATCHED")
                                        )
                                ),
                                new AnalysisEvidenceItem(
                                        "Dynatrace correlated component case-evaluation-service",
                                        List.of(
                                                new AnalysisEvidenceAttribute("dynatraceItemType", "component-status"),
                                                new AnalysisEvidenceAttribute("componentName", "case-evaluation-service"),
                                                new AnalysisEvidenceAttribute("correlationStatus", "MATCHED"),
                                                new AnalysisEvidenceAttribute("componentSignalStatus", "SIGNALS_PRESENT"),
                                                new AnalysisEvidenceAttribute("problemDisplayId", "P-26042756"),
                                                new AnalysisEvidenceAttribute("problemTitle", "Gateway timeout on backend"),
                                                new AnalysisEvidenceAttribute("signalCategories", "latency, failure-rate"),
                                                new AnalysisEvidenceAttribute(
                                                        "correlationHighlights",
                                                        "HTTP 5xx increase || response time degradation"
                                                ),
                                                new AnalysisEvidenceAttribute(
                                                        "summary",
                                                        "error rate increased, response time changed 85 -> 1840 ms"
                                                )
                                        )
                                ),
                                new AnalysisEvidenceItem(
                                        "Dynatrace metric service.response.time.p95 for case-evaluation-service",
                                List.of(
                                        new AnalysisEvidenceAttribute("metricLabel", "service.response.time.p95"),
                                        new AnalysisEvidenceAttribute("maxValue", "8.67"),
                                        new AnalysisEvidenceAttribute("unit", "ms")
                                )
                                )
                        )
                ), new AnalysisEvidenceSection(
                        "gitlab",
                        "resolved-code",
                        List.of(new AnalysisEvidenceItem(
                                "edge-client-service file src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java around line 8",
                                List.of(
                                        new AnalysisEvidenceAttribute("environment", "dev3"),
                                        new AnalysisEvidenceAttribute("branch", "release/2026.04"),
                                        new AnalysisEvidenceAttribute("group", "sample/runtime"),
                                        new AnalysisEvidenceAttribute("projectName", "edge-client-service"),
                                        new AnalysisEvidenceAttribute("filePath", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java"),
                                        new AnalysisEvidenceAttribute("referenceType", "STACKTRACE_SYMBOL"),
                                        new AnalysisEvidenceAttribute("symbol", "com.example.synthetic.edge.CatalogGatewayClient"),
                                        new AnalysisEvidenceAttribute("lineNumber", "8"),
                                        new AnalysisEvidenceAttribute("resolveScore", "95"),
                                        new AnalysisEvidenceAttribute("requestedStartLine", "5"),
                                        new AnalysisEvidenceAttribute("requestedEndLine", "12"),
                                        new AnalysisEvidenceAttribute("returnedStartLine", "5"),
                                        new AnalysisEvidenceAttribute("returnedEndLine", "12"),
                                        new AnalysisEvidenceAttribute("totalLines", "14"),
                                        new AnalysisEvidenceAttribute(
                                                "content",
                                                """
                                                        public class CatalogGatewayClient {
                                                            CatalogResponse fetchCatalog(String sku) {
                                                                return webClient.get();
                                                            }
                                                        }
                                                        """
                                        ),
                                        new AnalysisEvidenceAttribute("contentTruncated", "false")
                                )
                        ))
                ))
        );
    }

    private AnalysisAiAnalysisRequest requestWithoutToolCoveredEvidence() {
        return new AnalysisAiAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "sample/runtime",
                List.of(new AnalysisEvidenceSection(
                        "operational-context",
                        "matched-context",
                        List.of(new AnalysisEvidenceItem(
                                "Operational context summary",
                                List.of(
                                        new AnalysisEvidenceAttribute("team", "Billing Team"),
                                        new AnalysisEvidenceAttribute("process", "Rozliczenie katalogu")
                                )
                        ))
                ))
        );
    }

}
