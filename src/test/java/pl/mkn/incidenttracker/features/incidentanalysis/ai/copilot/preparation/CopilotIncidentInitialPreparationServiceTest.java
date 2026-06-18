package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.PreToolUseHookInput;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequest;
import com.github.copilot.rpc.PermissionRequestResultKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialRunAssembler;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentPromptRenderer;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicyFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabMcpTools;

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
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.artifactService;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotIncidentInitialPreparationServiceTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopilotSdkToolFactory toolFactory = toolFactory(
            List.of(
                    MethodToolCallbackProvider.builder()
                            .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                            .build()
            ),
            objectMapper,
            toolEvidenceSessionStore(objectMapper)
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
            assertEquals("C:\\tools\\copilot.exe", prepared.session().clientOptions().getCliPath());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.session().clientOptions().getCwd());
            assertEquals(Boolean.FALSE, prepared.session().clientOptions().getUseLoggedInUser().orElseThrow());
            assertEquals("test-token", prepared.session().clientOptions().getGithubToken());

            assertEquals("incidenttracker-test", prepared.session().sessionConfig().getClientName());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.session().sessionConfig().getWorkingDirectory());
            assertEquals("gpt-5.4", prepared.session().sessionConfig().getModel());
            assertEquals("medium", prepared.session().sessionConfig().getReasoningEffort());
            assertNotNull(prepared.session().sessionConfig().getSessionId());
            assertTrue(prepared.session().sessionConfig().getSessionId().startsWith("analysis-"));
            assertFalse(prepared.session().sessionConfig().isStreaming());
            assertEquals(
                    Set.of(
                            "gitlab_find_class_references",
                            "gitlab_find_flow_context",
                            "gitlab_list_available_repositories",
                            "gitlab_read_repository_file_chunk",
                            "gitlab_read_repository_file_chunks",
                            "gitlab_read_repository_files_by_path",
                            "gitlab_read_repository_file_outline",
                            "skill"
                    ),
                    Set.copyOf(prepared.session().sessionConfig().getAvailableTools())
            );
            assertEquals(7, prepared.session().sessionConfig().getTools().size());
            assertEquals(
                    CopilotIncidentRuntimeSkillNames.allSkillNames(),
                    skillDirectoryNames(prepared.session().sessionConfig().getSkillDirectories())
            );
            assertEquals(PermissionHandler.APPROVE_ALL, prepared.session().sessionConfig().getOnPermissionRequest());
            assertEquals(List.of(), prepared.session().sessionConfig().getDisabledSkills());
            assertNotNull(prepared.session().sessionConfig().getHooks());

            var prompt = prepared.session().messageOptions().getPrompt();
            assertTrue(prompt.contains("correlationId: timeout-123"));
            assertTrue(prompt.contains("environment: dev3"));
            assertTrue(prompt.contains("gitLabBranch: release/2026.04"));
            assertTrue(prompt.contains("gitLabGroup: CRM/runtime"));
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
            assertTrue(prompt.contains("Read timed out while calling crm-customer-profile-service"));
            assertTrue(prompt.contains("Problem `P-26042756` `Gateway timeout on backend`."));
            assertTrue(prompt.contains("GitLab resolved code references"));
            assertTrue(prompt.contains("Available capability groups:"));
            assertTrue(prompt.contains("GitLab code: inspect class references/imports, focused chunks, outlines or flow context only for listed code, flow, technical-analysis or DB code-grounding gaps."));
            assertTrue(prompt.contains("enterprise software incident analysis"));
            assertTrue(prompt.contains("operator, tester, analyst, or junior/mid developer"));
            assertTrue(prompt.contains("may not know the affected system area"));
            assertTrue(prompt.contains("Hard rules:"));
            assertTrue(prompt.contains("Treat environment, gitLabBranch and gitLabGroup as fixed session context."));
            assertTrue(prompt.contains("Only the explicitly listed capability groups are enabled for this session."));
            assertTrue(prompt.contains("Local workspace, filesystem and shell or terminal tools are blocked. Do not inspect the local disk."));
            assertTrue(prompt.contains("Do not invent environment, branch, group, project, table, owner, process, bounded context, or downstream system."));
            assertTrue(prompt.contains("The built-in `skill` tool is enabled for runtime skills."));
            assertTrue(prompt.contains("At the start of the initial diagnosis, load the starter skill `incident-analysis-orchestrator`"));
            assertTrue(prompt.contains("Do not claim that you know a skill definition, SKILL.md content, or detailed skill rules unless you loaded that skill through the `skill` tool in this session."));
            assertTrue(prompt.contains("Runtime skills:"));
            assertTrue(prompt.contains("starter skill to load before classifying the incident: `incident-analysis-orchestrator`"));
            assertTrue(prompt.contains("diagnostic skills to load only when required by the starter algorithm: incident-operational-context-tools, incident-analysis-gitlab-tools, incident-data-diagnostics"));
            assertTrue(prompt.contains("result skills to load after diagnosis for final answer synthesis: incident-functional-analysis, incident-technical-handoff"));
            assertTrue(prompt.contains("Use tools only for evidence gaps listed in `evidenceCoverage.gaps`"));
            assertTrue(prompt.contains("Do not use tools just because they are available."));
            assertTrue(prompt.contains("GitLab, Elasticsearch, Operational Context and Database tools are coverage-aware"));
            assertTrue(prompt.contains("Treat `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED` as a targeted gap for `functionalAnalysis`"));
            assertTrue(prompt.contains("Treat `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` as a targeted gap for `technicalAnalysis`"));
            assertTrue(prompt.contains("`functionalAnalysis` must avoid implementation-first language."));
            assertTrue(prompt.contains("`technicalAnalysis` must follow Technical Handoff v1"));
            assertTrue(prompt.contains("Before the first DB table/column/schema-table query for a JPA, repository or data-access symptom"));
            assertTrue(prompt.contains("Use deterministic GitLab evidence or enabled GitLab tools to identify the entity, repository predicate, likely table/column names and direct relations that should guide DB diagnostics."));
            assertTrue(prompt.contains("If an exception, stacktrace or deterministic code evidence grounds a class name, use GitLab class-reference or flow tools with grounded class names and focused keywords before broad DB discovery"));
            assertTrue(prompt.contains("Every Elasticsearch HTTP diagnostic tool call, GitLab tool call, Database tool call and Operational Context tool call must include `reason`: one short Polish sentence"));
            assertTrue(prompt.contains("When Elasticsearch HTTP diagnostic tools are enabled and the evidence points to an opaque external/downstream HTTP failure"));
            assertTrue(prompt.contains("`elastic_fetch_http_call_logs` uses the current hidden correlationId only when `path` is omitted"));
            assertTrue(prompt.contains("If visibility is incomplete, state exactly what remains unverified and what the next verification step is."));
            assertTrue(prompt.contains("Return the analysis in Polish."));
            assertTrue(prompt.contains("Return only valid JSON."));
            assertTrue(prompt.contains("The final answer must start with `{` and end with `}`."));
            assertTrue(prompt.contains("Do not wrap it in Markdown."));
            assertTrue(prompt.contains("Do not add prose before or after JSON."));
            assertTrue(prompt.contains("Do not add status text such as \"I have all the evidence needed\" before the JSON."));
            assertTrue(prompt.contains("Use concise professional Markdown in string values where the schema says markdown string."));
            assertTrue(prompt.contains("Use `code spans` for technical identifiers"));
            assertTrue(prompt.contains("Never join multiple points with pipe separators like \"|\"."));
            assertTrue(prompt.contains("Required schema:"));
            assertTrue(prompt.contains("\"detectedProblem\": \"string\""));
            assertTrue(prompt.contains("\"functionalAnalysis\": \"markdown string in Polish, Functional Analysis v1\""));
            assertTrue(prompt.contains("\"technicalAnalysis\": \"markdown string in Polish, Technical Handoff v1\""));
            assertTrue(prompt.contains("\"confidence\": \"high|medium|low\""));
            assertTrue(prompt.contains("\"visibilityLimits\": [\"string\"]"));
            assertTrue(prompt.contains("`functionalAnalysis` must follow Functional Analysis v1"));
            assertTrue(prompt.contains("`technicalAnalysis` must follow Technical Handoff v1"));
            assertFalse(prompt.contains("metricLabel=service.response.time.p95"));
            assertEquals(prompt, prepared.prompt());

            assertTrue(prepared.session().messageOptions().getAttachments() == null || prepared.session().messageOptions().getAttachments().isEmpty());
            assertEquals(5, prepared.session().artifactContents().size());
            var manifestContent = prepared.session().artifactContents().get("00-incident-manifest.json");
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
            assertTrue(manifestContent.contains("\"skill\""));
            assertTrue(manifestContent.contains("\"enabledIncidentToolNames\""));
            assertTrue(manifestContent.contains("\"platformBuiltInToolNames\""));
            assertTrue(manifestContent.contains("\"runtimeSkills\""));
            assertTrue(manifestContent.contains("\"skillToolAvailable\" : true"));
            assertTrue(manifestContent.contains("\"skillDirectoriesConfigured\" : true"));
            assertTrue(manifestContent.contains("\"starterSkillName\" : \"incident-analysis-orchestrator\""));
            assertTrue(manifestContent.contains("\"diagnosticSkillNames\""));
            assertTrue(manifestContent.contains("\"resultSkillNames\""));
            assertTrue(manifestContent.contains("\"incident-operational-context-tools\""));
            assertTrue(manifestContent.contains("\"incident-analysis-gitlab-tools\""));
            assertTrue(manifestContent.contains("\"incident-data-diagnostics\""));
            assertTrue(manifestContent.contains("\"incident-functional-analysis\""));
            assertTrue(manifestContent.contains("\"incident-technical-handoff\""));
            assertTrue(manifestContent.contains("\"loadStarterBeforeDiagnosis\" : true"));
            assertTrue(manifestContent.contains("\"loadDiagnosticSkillsOnlyWhenRequired\" : true"));
            assertTrue(manifestContent.contains("\"loadResultSkillsAfterDiagnosis\" : true"));
            assertTrue(manifestContent.contains("\"claimSkillContentsOnlyAfterSkillToolCall\" : true"));
            assertTrue(manifestContent.contains("\"evidenceCoverage\""));
            assertTrue(manifestContent.contains("\"gitLab\" : \"DIRECT_COLLABORATOR_ATTACHED\""));
            assertTrue(manifestContent.contains("\"code\" : \"MISSING_FLOW_CONTEXT\""));
            assertTrue(manifestContent.contains("\"code\" : \"TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED\""));
            assertTrue(manifestContent.contains("\"deliveryMode\" : \"embedded-prompt\""));
            assertTrue(manifestContent.contains("\"artifactsArePrimarySourceOfTruth\" : true"));

            var digestContent = prepared.session().artifactContents().get("01-incident-digest.md");
            assertTrue(digestContent.contains("# Incident digest"));
            assertTrue(digestContent.contains("- correlationId: `timeout-123`"));
            assertTrue(digestContent.contains("- GitLab: `DIRECT_COLLABORATOR_ATTACHED`"));
            assertTrue(digestContent.contains("## Known evidence gaps"));

            var logsContent = prepared.session().artifactContents().get("02-elasticsearch-logs.md");
            assertTrue(logsContent.contains("Elasticsearch log evidence"));
            assertTrue(logsContent.contains("## itemId: elastic-logs-001"));
            assertTrue(logsContent.contains("Log entry `1` `ERROR` `crm-billing-service`"));
            assertTrue(logsContent.contains("- message:"));
            assertTrue(logsContent.contains("Read timed out while calling crm-customer-profile-service"));
            assertFalse(logsContent.contains("\"provider\""));

            var dynatraceContent = prepared.session().artifactContents().get("03-dynatrace-runtime-signals.md");
            assertTrue(dynatraceContent.contains("Dynatrace runtime signals"));
            assertTrue(dynatraceContent.contains("## itemId: dynatrace-runtime-signals-001"));
            assertTrue(dynatraceContent.contains("- collection status: COLLECTED"));
            assertTrue(dynatraceContent.contains("component `case-evaluation-service`: MATCHED, SIGNALS_PRESENT."));
            assertTrue(dynatraceContent.contains("Problem `P-26042756` `Gateway timeout on backend`."));
            assertFalse(dynatraceContent.contains("\"metricLabel\""));

            var gitLabContent = prepared.session().artifactContents().get("04-gitlab-resolved-code.md");
            assertTrue(gitLabContent.contains("GitLab resolved code references"));
            assertTrue(gitLabContent.contains("## itemId: gitlab-resolved-code-001"));
            assertTrue(gitLabContent.contains("- repository: `crm-customer-client-service`"));
            assertTrue(gitLabContent.contains("- returned lines: `5-12` of `14`"));
            assertTrue(gitLabContent.contains("```java"));
            assertFalse(gitLabContent.contains("\"content\""));

            var deniedToolDecision = prepared.session().sessionConfig().getHooks().getOnPreToolUse()
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
        var factory = mock(CopilotSdkToolFactory.class);
        var expectedTools = List.of(ToolDefinition.createSkipPermission(
                "gitlab_read_repository_file",
                "Read repository file",
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        ));
        when(factory.createToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(expectedTools);

        var service = new CopilotIncidentInitialPreparationService(
                runAssembler(properties, factory),
                runPreparationService(properties)
        );

        try (var prepared = service.prepare(request)) {
            verify(factory).createToolDefinitions(org.mockito.ArgumentMatchers.argThat(context ->
                    "timeout-123".equals(context.correlationId())
                            && "dev3".equals(context.environment())
                            && "release/2026.04".equals(context.gitLabBranch())
                            && "CRM/runtime".equals(context.gitLabGroup())
                            && context.analysisRunId() != null
                            && !context.analysisRunId().isBlank()
                            && context.copilotSessionId() != null
                            && context.copilotSessionId().startsWith("analysis-")
            ));
            assertEquals(expectedTools, prepared.session().sessionConfig().getTools());
            assertEquals(List.of("gitlab_read_repository_file", "skill"), prepared.session().sessionConfig().getAvailableTools());
            var allowedToolDecision = prepared.session().sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("gitlab_read_repository_file"), null)
                    .join();
            var skillToolDecision = prepared.session().sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("skill"), null)
                    .join();
            assertEquals("allow", allowedToolDecision.permissionDecision());
            assertEquals("allow", skillToolDecision.permissionDecision());
            var deniedToolDecision = prepared.session().sessionConfig().getHooks().getOnPreToolUse()
                    .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                    .join();
            assertEquals("deny", deniedToolDecision.permissionDecision());
            assertNotNull(prepared.session().sessionConfig().getSessionId());
            assertTrue(prepared.session().sessionConfig().getSessionId().startsWith("analysis-"));
        }
    }

    @Test
    void shouldAllowOnlyExplicitSpringToolsWhenArtifactsDoNotAlreadyCoverThem() {
        var properties = baseProperties();
        var request = requestWithoutToolCoveredEvidence();
        var factory = mock(CopilotSdkToolFactory.class);
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
        when(factory.createToolDefinitions(any(CopilotToolSessionContext.class))).thenReturn(expectedTools);

        var service = new CopilotIncidentInitialPreparationService(
                runAssembler(properties, factory),
                runPreparationService(properties)
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of(expectedTools.get(0)), prepared.session().sessionConfig().getTools());
            assertEquals(
                    Set.of("gitlab_read_repository_file", "skill"),
                    Set.copyOf(prepared.session().sessionConfig().getAvailableTools())
            );
        }
    }

    @Test
    void shouldFilterRegisteredToolsWhenCoverageDoesNotAllowTheirSpecificUse() {
        var properties = baseProperties();
        var request = sampleRequest();
        var factory = mock(CopilotSdkToolFactory.class);
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
        when(factory.createToolDefinitions(any(CopilotToolSessionContext.class)))
                .thenReturn(List.of(elasticTool, gitLabTool, dbTool));

        var service = new CopilotIncidentInitialPreparationService(
                runAssembler(properties, factory),
                runPreparationService(properties)
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of(), prepared.session().sessionConfig().getTools());
            assertEquals(List.of("skill"), prepared.session().sessionConfig().getAvailableTools());
            assertFalse(prepared.prompt().contains("Database diagnostics:"));
            assertFalse(prepared.prompt().contains("Elasticsearch logs: fetch additional logs"));
            assertFalse(prepared.prompt().contains("GitLab code: inspect class references/imports"));
            var deniedElasticDecision = prepared.session().sessionConfig().getHooks().getOnPreToolUse()
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
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "CRM/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals("ghp_test_token", prepared.session().clientOptions().getGithubToken());
            assertEquals(Boolean.FALSE, prepared.session().clientOptions().getUseLoggedInUser().orElseThrow());
        }
    }

    @Test
    void shouldUseRequestAiOptionsForSessionConfigWhenProvided() {
        var properties = baseProperties();
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");

        var service = createService(properties);
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "CRM/runtime",
                List.of(),
                new AnalysisAiOptions("gpt-5.3-codex", "high")
        );

        try (var prepared = service.prepare(request)) {
            assertEquals("gpt-5.3-codex", prepared.session().sessionConfig().getModel());
            assertEquals("high", prepared.session().sessionConfig().getReasoningEffort());
        }
    }

    @Test
    void shouldDisableLoggedInUserFallbackWhenGithubTokenIsMissingInTestFactory() {
        var properties = baseProperties();
        var service = createService(properties);
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "CRM/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals("incidenttracker", prepared.session().sessionConfig().getClientName());
            assertEquals("C:\\Users\\mknie\\IdeaProjects\\incidenttracker", prepared.session().sessionConfig().getWorkingDirectory());
            assertEquals(Boolean.FALSE, prepared.session().clientOptions().getUseLoggedInUser().orElseThrow());
            assertEquals("test-token", prepared.session().clientOptions().getGithubToken());
            assertEquals(null, prepared.session().sessionConfig().getModel());
            assertEquals(null, prepared.session().sessionConfig().getReasoningEffort());
            assertEquals(
                    CopilotIncidentRuntimeSkillNames.allSkillNames(),
                    skillDirectoryNames(prepared.session().sessionConfig().getSkillDirectories())
            );
        }
    }

    @Test
    void shouldPassDisabledSkillsToSessionConfig() {
        var properties = baseProperties();
        properties.setDisabledSkills(List.of("incident-analysis-gitlab-tools"));

        var service = createService(properties);
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "CRM/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            assertEquals(List.of("incident-analysis-gitlab-tools"), prepared.session().sessionConfig().getDisabledSkills());
        }
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = baseProperties();
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);

        var service = createService(properties);
        var request = new InitialAnalysisRequest(
                "corr-123",
                "dev1",
                "main",
                "CRM/runtime",
                List.of()
        );

        try (var prepared = service.prepare(request)) {
            var decision = prepared.session().sessionConfig()
                    .getOnPermissionRequest()
                    .handle(new PermissionRequest(), null)
                    .join();

            assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
        }
    }

    private CopilotIncidentInitialPreparationService createService(CopilotSdkProperties properties) {
        return new CopilotIncidentInitialPreparationService(
                runAssembler(properties, toolFactory),
                runPreparationService(properties)
        );
    }

    private CopilotIncidentInitialRunAssembler runAssembler(
            CopilotSdkProperties properties,
            CopilotSdkToolFactory toolFactory
    ) {
        return new CopilotIncidentInitialRunAssembler(
                toolFactory,
                new CopilotIncidentToolSessionContextFactory(new CopilotIncidentHiddenToolContextFactory()),
                new CopilotIncidentSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties)),
                artifactService(objectMapper),
                policyFactory(),
                promptRenderer(),
                new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper())
        );
    }

    private CopilotIncidentToolAccessPolicyFactory policyFactory() {
        return new CopilotIncidentToolAccessPolicyFactory(new CopilotIncidentEvidenceCoverageEvaluator());
    }

    private CopilotIncidentPromptRenderer promptRenderer() {
        return new CopilotIncidentPromptRenderer();
    }

    private CopilotRunPreparationService runPreparationService(CopilotSdkProperties properties) {
        return new CopilotRunPreparationService(
                new CopilotPreparedSessionFactory(new CopilotSessionConfigFactory(properties))
        );
    }

    private List<String> skillDirectoryNames(List<String> skillDirectories) {
        return skillDirectories.stream()
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
    }

    private CopilotSdkProperties baseProperties() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\Users\\mknie\\IdeaProjects\\incidenttracker");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        return properties;
    }

    private InitialAnalysisRequest sampleRequest() {
        return new InitialAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "CRM/runtime",
                List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(new AnalysisEvidenceItem(
                                "crm-billing-service log entry",
                                List.of(
                                        new AnalysisEvidenceAttribute("timestamp", "2026-04-11T20:57:33.285Z"),
                                        new AnalysisEvidenceAttribute("level", "ERROR"),
                                        new AnalysisEvidenceAttribute("serviceName", "crm-billing-service"),
                                        new AnalysisEvidenceAttribute("className", "com.example.synthetic.CustomerBillingService"),
                                        new AnalysisEvidenceAttribute("message", "Read timed out while calling crm-customer-profile-service")
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
                                "crm-customer-client-service file src/main/java/com/example/synthetic/edge/CustomerProfileClient.java around line 8",
                                List.of(
                                        new AnalysisEvidenceAttribute("environment", "dev3"),
                                        new AnalysisEvidenceAttribute("branch", "release/2026.04"),
                                        new AnalysisEvidenceAttribute("group", "CRM/runtime"),
                                        new AnalysisEvidenceAttribute("projectName", "crm-customer-client-service"),
                                        new AnalysisEvidenceAttribute("filePath", "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java"),
                                        new AnalysisEvidenceAttribute("referenceType", "STACKTRACE_SYMBOL"),
                                        new AnalysisEvidenceAttribute("symbol", "com.example.synthetic.edge.CustomerProfileClient"),
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
                                                        public class CustomerProfileClient {
                                                            CustomerProfileResponse fetchCustomerProfile(String sku) {
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

    private InitialAnalysisRequest requestWithoutToolCoveredEvidence() {
        return new InitialAnalysisRequest(
                "timeout-123",
                "dev3",
                "release/2026.04",
                "CRM/runtime",
                List.of(new AnalysisEvidenceSection(
                        "operational-context",
                        "matched-context",
                        List.of(new AnalysisEvidenceItem(
                                "Operational context summary",
                                List.of(
                                        new AnalysisEvidenceAttribute("team", "CRM Team"),
                                        new AnalysisEvidenceAttribute("process", "Rozliczenie katalogu")
                                )
                        ))
                ))
        );
    }

}
