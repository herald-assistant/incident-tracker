package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerSectionModeRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class FlowExplorerCopilotRuntimePreparationTest {

    private static final CopilotToolDescriptionContext FLOW_EXPLORER_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    @TempDir
    Path tempDirectory;

    @Test
    void shouldAllowOnlyFlowExplorerGitLabOperationalContextAndFeedbackTools() {
        var gitLabTool = tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK);
        var javaMethodContextTool = tool(GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT);
        var operationalContextTool = tool(OperationalContextToolNames.SEARCH);
        var databaseTool = tool(DatabaseToolNames.COUNT_ROWS);
        var elasticTool = tool(ElasticToolNames.SEARCH_LOGS_BY_CORRELATION_ID);
        var feedbackTool = tool(CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK);
        var reportGetCurrentTool = tool(CopilotReportToolNames.GET_CURRENT);
        var reportUpsertSectionTool = tool(CopilotReportToolNames.UPSERT_SECTION);
        var reportUpdateHeaderTool = tool(CopilotReportToolNames.UPDATE_HEADER);
        var reportUpdateMetaTool = tool(CopilotReportToolNames.UPDATE_META);

        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(
                databaseTool,
                gitLabTool,
                javaMethodContextTool,
                elasticTool,
                operationalContextTool,
                feedbackTool,
                reportGetCurrentTool,
                reportUpsertSectionTool,
                reportUpdateHeaderTool,
                reportUpdateMetaTool
        ));

        assertEquals(
                List.of(
                        GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                        GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT,
                        OperationalContextToolNames.SEARCH,
                        CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
                        CopilotReportToolNames.GET_CURRENT,
                        CopilotReportToolNames.UPSERT_SECTION,
                        CopilotReportToolNames.UPDATE_HEADER,
                        CopilotReportToolNames.UPDATE_META
                ),
                policy.availableToolNames()
        );
        assertEquals(
                List.of(
                        gitLabTool,
                        javaMethodContextTool,
                        operationalContextTool,
                        feedbackTool,
                        reportGetCurrentTool,
                        reportUpsertSectionTool,
                        reportUpdateHeaderTool,
                        reportUpdateMetaTool
                ),
                policy.enabledTools()
        );
        assertTrue(policy.localWorkspaceAccessBlocked());
        assertTrue(policy.gitLabToolsRegistered());
        assertTrue(policy.operationalContextToolsRegistered());
        assertTrue(policy.databaseToolsRegistered());
        assertTrue(policy.elasticToolsRegistered());
        assertTrue(policy.gitLabToolsEnabled());
        assertTrue(policy.operationalContextToolsEnabled());
        assertTrue(policy.toolFeedbackEnabled());
        assertTrue(policy.reportToolsEnabled());
        assertFalse(policy.databaseToolsEnabled());
        assertFalse(policy.elasticToolsEnabled());
    }

    @Test
    void shouldBuildRuntimeOnlyHiddenToolContext() {
        var contextFactory = new FlowExplorerCopilotToolSessionContextFactory();

        var sessionContext = contextFactory.create(
                "job-123",
                request(),
                contextSnapshot(),
                preparation()
        );
        var hiddenContext = sessionContext.hiddenContext();

        assertEquals("job-123", sessionContext.analysisRunId());
        assertEquals("flow-explorer-job-123", sessionContext.copilotSessionId());
        assertEquals("job-123", hiddenContext.get(AgentToolContextKeys.ANALYSIS_RUN_ID));
        assertEquals("flow-explorer-job-123", hiddenContext.get(AgentToolContextKeys.COPILOT_SESSION_ID));
        assertEquals(9, hiddenContext.size());
        assertEquals(
                FlowExplorerCopilotToolContextKeys.FEATURE_VALUE,
                hiddenContext.get(FlowExplorerCopilotToolContextKeys.FEATURE)
        );
        assertEquals(
                FlowExplorerCopilotToolContextKeys.RUN_KIND_INITIAL,
                hiddenContext.get(FlowExplorerCopilotToolContextKeys.RUN_KIND)
        );
        assertEquals(true, hiddenContext.get(FlowExplorerCopilotToolContextKeys.ENDPOINT_CONTEXT_EMBEDDED));
        assertEquals(true, hiddenContext.get(FlowExplorerCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED));
        assertInstanceOf(String.class, hiddenContext.get(AgentToolContextKeys.REPORT_ID));
        assertTrue(((String) hiddenContext.get(AgentToolContextKeys.REPORT_ID)).startsWith("report-"));
        assertEquals("flow-explorer", hiddenContext.get(AgentToolContextKeys.REPORT_FEATURE));
        assertEquals(
                List.of("OVERVIEW", "FUNCTIONAL_FLOW", "VALIDATIONS", "PERSISTENCE", "INTEGRATIONS"),
                hiddenContext.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS)
        );
        assertFalse(hiddenContext.containsKey(AgentToolContextKeys.CORRELATION_ID));
        assertFalse(hiddenContext.containsKey(AgentToolContextKeys.GITLAB_GROUP));
        assertFalse(hiddenContext.containsKey(AgentToolContextKeys.GITLAB_BRANCH));
    }

    @Test
    void shouldLimitFlowExplorerReportSectionsToActiveSections() {
        var contextFactory = new FlowExplorerCopilotToolSessionContextFactory();

        var sessionContext = contextFactory.create(
                "job-123",
                requestWithSectionModes(),
                contextSnapshot(),
                preparation()
        );

        assertEquals(
                List.of("OVERVIEW", "FUNCTIONAL_FLOW", "PERSISTENCE"),
                sessionContext.hiddenContext().get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS)
        );
    }

    @Test
    void shouldBuildSessionConfigWithFlowExplorerSkillsOnly() {
        var tool = tool(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT);
        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(tool));
        var factory = sessionConfigRequestFactory();

        var request = factory.create(
                "flow-explorer-job-123",
                policy,
                request().aiOptions(),
                request().goal()
        );

        assertEquals("flow-explorer-job-123", request.sessionId());
        assertEquals(List.of(tool), request.tools());
        assertEquals(List.of(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT), request.availableToolNames());
        assertSkillDirectories(
                request.skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(FlowExplorerAnalysisGoal.DEEP_DISCOVERY)
        );
        assertTrue(request.effectiveAvailableToolNames().contains("skill"));
        assertEquals("gpt-5.4", request.modelSelection().model());
        assertEquals("medium", request.modelSelection().reasoningEffort());
        assertEquals(
                "Use only the inline Flow Explorer artifacts and the explicitly enabled Flow Explorer tools for this session.",
                request.deniedToolUseMessage()
        );
    }

    @Test
    void shouldBuildSessionConfigWithTestScenariosGoalSkillOnly() {
        var tool = tool(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT);
        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(tool));
        var factory = sessionConfigRequestFactory();

        var request = factory.create(
                "flow-explorer-job-123",
                policy,
                testScenariosRequest().aiOptions(),
                testScenariosRequest().goal()
        );

        assertSkillDirectories(
                request.skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(FlowExplorerAnalysisGoal.TEST_SCENARIOS)
        );
        assertSelectedSkillIncluded(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.TEST_SCENARIOS_SKILL_NAME);
        assertSelectedSkillMissing(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.DEEP_DISCOVERY_SKILL_NAME);
    }

    @Test
    void shouldBuildSessionConfigWithRiskDetectionGoalSkillOnly() {
        var tool = tool(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT);
        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(tool));
        var factory = sessionConfigRequestFactory();

        var request = factory.create(
                "flow-explorer-job-123",
                policy,
                riskDetectionRequest().aiOptions(),
                riskDetectionRequest().goal()
        );

        assertSkillDirectories(
                request.skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(FlowExplorerAnalysisGoal.RISK_DETECTION)
        );
        assertSelectedSkillIncluded(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.RISK_DETECTION_SKILL_NAME);
        assertSelectedSkillMissing(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.DEEP_DISCOVERY_SKILL_NAME);
        assertSelectedSkillMissing(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.TEST_SCENARIOS_SKILL_NAME);
    }

    @Test
    void shouldBuildFollowUpSessionConfigWithChatSkillWithoutResultContractSkill() {
        var tool = tool(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT);
        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(tool));
        var factory = sessionConfigRequestFactory();

        var request = factory.createForFollowUp(
                "flow-explorer-follow-up-123",
                policy,
                request().aiOptions()
        );

        assertEquals("flow-explorer-follow-up-123", request.sessionId());
        assertEquals(List.of(tool), request.tools());
        assertEquals(List.of(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT), request.availableToolNames());
        assertSkillDirectories(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames());
        assertSelectedSkillIncluded(request.skillDirectories(), FlowExplorerCopilotRuntimeSkillNames.FOLLOW_UP_CHAT_SKILL_NAME);
        assertSelectedSkillMissing(request.skillDirectories(), "flow-explorer-orchestrator");
        assertSelectedSkillMissing(request.skillDirectories(), "flow-explorer-result-contract");
    }

    @Test
    void shouldAssembleCopilotRunRequestWithoutExecutingAi() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = new FlowExplorerCopilotToolSessionContextFactory();
        var toolAccessPolicyFactory = new FlowExplorerCopilotToolAccessPolicyFactory();
        var sessionConfigRequestFactory = sessionConfigRequestFactory();
        var assembler = new FlowExplorerCopilotRunRequestAssembler(
                toolFactory,
                toolSessionContextFactory,
                toolAccessPolicyFactory,
                sessionConfigRequestFactory
        );
        var gitLabTool = tool(GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH);
        var databaseTool = tool(DatabaseToolNames.DESCRIBE_TABLE);
        var feedbackTool = tool(CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);

        when(toolFactory.createToolDefinitions(
                contextCaptor.capture(),
                eq(FLOW_EXPLORER_DESCRIPTION_CONTEXT)
        ))
                .thenReturn(List.of(gitLabTool, databaseTool, feedbackTool));

        var assembly = assembler.assemble(
                "job-123",
                request(),
                contextSnapshot(),
                preparation()
        );
        var runRequest = assembly.runRequest();

        assertEquals("job-123", runRequest.runReference());
        assertEquals("Flow Explorer canonical prompt", runRequest.prompt());
        assertEquals(preparation().artifactContents(), runRequest.artifactContents());
        assertNotNull(runRequest.initialReport());
        assertEquals(
                assembly.toolSessionContext().hiddenContext().get(AgentToolContextKeys.REPORT_ID),
                runRequest.initialReport().reportId()
        );
        assertEquals("Flow Explorer: GET /api/customers/{id}", runRequest.initialReport().header());
        assertEquals(
                List.of("OVERVIEW", "FUNCTIONAL_FLOW", "VALIDATIONS", "PERSISTENCE", "INTEGRATIONS"),
                runRequest.initialReport().sections().stream()
                        .map(section -> section.id())
                        .toList()
        );
        assertSame(assembly.toolSessionContext(), contextCaptor.getValue());
        assertEquals(
                List.of(
                        GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                        CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK
                ),
                runRequest.sessionConfigRequest().availableToolNames()
        );
        assertSkillDirectories(
                runRequest.sessionConfigRequest().skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.initialSkillNames(FlowExplorerAnalysisGoal.DEEP_DISCOVERY)
        );
        assertFalse(assembly.toolAccessPolicy().databaseToolsEnabled());

        verify(toolFactory).createToolDefinitions(
                assembly.toolSessionContext(),
                FLOW_EXPLORER_DESCRIPTION_CONTEXT
        );
    }

    @Test
    void shouldAssembleFollowUpCopilotRunRequestWithoutResultContractSkill() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var toolSessionContextFactory = new FlowExplorerCopilotToolSessionContextFactory();
        var toolAccessPolicyFactory = new FlowExplorerCopilotToolAccessPolicyFactory();
        var sessionConfigRequestFactory = sessionConfigRequestFactory();
        var assembler = new FlowExplorerCopilotRunRequestAssembler(
                toolFactory,
                toolSessionContextFactory,
                toolAccessPolicyFactory,
                sessionConfigRequestFactory
        );
        var gitLabTool = tool(GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);

        when(toolFactory.createToolDefinitions(
                contextCaptor.capture(),
                eq(FLOW_EXPLORER_DESCRIPTION_CONTEXT)
        ))
                .thenReturn(List.of(gitLabTool));

        var assembly = assembler.assembleFollowUp(
                "follow-up-123",
                request(),
                contextSnapshot(),
                preparation(),
                "initial-copilot-session-123",
                AnalysisAiAuthRef.localToken(null)
        );
        var runRequest = assembly.runRequest();
        var hiddenContext = assembly.toolSessionContext().hiddenContext();

        assertEquals("follow-up-123", runRequest.runReference());
        assertNull(runRequest.initialReport());
        assertEquals(CopilotSessionTarget.Type.EXISTING, runRequest.sessionTarget().type());
        assertEquals("initial-copilot-session-123", runRequest.sessionTarget().sessionId());
        assertEquals("initial-copilot-session-123", assembly.toolSessionContext().copilotSessionId());
        assertEquals("Flow Explorer canonical prompt", runRequest.prompt());
        assertEquals(
                FlowExplorerCopilotToolContextKeys.RUN_KIND_FOLLOW_UP,
                hiddenContext.get(FlowExplorerCopilotToolContextKeys.RUN_KIND)
        );
        assertSkillDirectories(
                runRequest.sessionConfigRequest().skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames()
        );
        assertSelectedSkillIncluded(runRequest.sessionConfigRequest().skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.FOLLOW_UP_CHAT_SKILL_NAME);
        assertSelectedSkillMissing(runRequest.sessionConfigRequest().skillDirectories(), "flow-explorer-orchestrator");
        assertSelectedSkillMissing(runRequest.sessionConfigRequest().skillDirectories(), "flow-explorer-result-contract");

        verify(toolFactory).createToolDefinitions(
                assembly.toolSessionContext(),
                FLOW_EXPLORER_DESCRIPTION_CONTEXT
        );
    }

    private FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory() {
        var properties = new CopilotSdkProperties();
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        return new FlowExplorerCopilotSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties));
    }

    private static void assertSkillDirectories(List<String> skillDirectories) {
        assertSkillDirectories(skillDirectories, FlowExplorerCopilotRuntimeSkillNames.allSkillNames());
    }

    private static void assertSkillDirectories(List<String> skillDirectories, List<String> expectedSkillNames) {
        assertEquals(1, skillDirectories.size());
        var selectedRoot = Path.of(skillDirectories.get(0));
        assertTrue(Files.isDirectory(selectedRoot));
        for (var expectedSkillName : expectedSkillNames) {
            assertTrue(
                    Files.isRegularFile(selectedRoot.resolve(expectedSkillName).resolve("SKILL.md")),
                    () -> "Missing selected skill in root: " + expectedSkillName
            );
        }
        assertFalse(Files.exists(selectedRoot.resolve("incident-analysis-orchestrator")));
    }

    private static void assertSelectedSkillIncluded(List<String> skillDirectories, String skillName) {
        assertEquals(1, skillDirectories.size());
        assertTrue(Files.isRegularFile(Path.of(skillDirectories.get(0)).resolve(skillName).resolve("SKILL.md")));
    }

    private static void assertSelectedSkillMissing(List<String> skillDirectories, String skillName) {
        assertEquals(1, skillDirectories.size());
        assertFalse(Files.exists(Path.of(skillDirectories.get(0)).resolve(skillName)));
    }

    private static FlowExplorerJobStartRequest request() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                null,
                "Skup sie na jezyku zrozumialym dla testera.",
                "gpt-5.4",
                "medium"
        );
    }

    private static FlowExplorerJobStartRequest testScenariosRequest() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW, FlowExplorerFocusArea.VALIDATIONS),
                null,
                "Skup sie na scenariuszach regresyjnych CRM.",
                "gpt-5.4",
                "high"
        );
    }

    private static FlowExplorerJobStartRequest riskDetectionRequest() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.RISK_DETECTION,
                List.of(FlowExplorerFocusArea.VALIDATIONS, FlowExplorerFocusArea.INTEGRATIONS),
                null,
                "Skup sie na ryzykach regresji CRM.",
                "gpt-5.4",
                "high"
        );
    }

    private static FlowExplorerJobStartRequest requestWithSectionModes() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(),
                List.of(
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                                FlowExplorerResultSectionMode.DEEP
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.VALIDATIONS,
                                FlowExplorerResultSectionMode.OFF
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.PERSISTENCE,
                                FlowExplorerResultSectionMode.COMPACT
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.INTEGRATIONS,
                                FlowExplorerResultSectionMode.OFF
                        )
                ),
                "Skup sie na jezyku zrozumialym dla testera.",
                "gpt-5.4",
                "medium"
        );
    }

    private static FlowExplorerPromptPreparation preparation() {
        return new FlowExplorerPromptPreparation(
                "Flow Explorer canonical prompt",
                List.of(),
                Map.of(
                        "flow-explorer/context-snapshot.json", "{\"systemId\":\"crm-service\"}",
                        "flow-explorer/snippet-cards.md", "# Snippets"
                )
        );
    }

    private static FlowExplorerContextSnapshot contextSnapshot() {
        return new FlowExplorerContextSnapshot(
                "crm-service",
                "CRM Service",
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                "GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                new FlowExplorerEndpointContext(
                        "GET:/api/customers/{id}",
                        List.of("GET"),
                        "/api/customers/{id}",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer",
                        "src/main/java/com/example/CustomerController.java",
                        12,
                        24,
                        "HIGH"
                ),
                List.of(new FlowExplorerRepositoryContext(
                        "crm-service",
                        "crm-service",
                        "platform/backend/crm-service",
                        "feature/FLOW-42",
                        true,
                        true,
                        List.of()
                )),
                List.of(new FlowExplorerFlowNode(
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        "src/main/java/com/example/CustomerController.java",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        "Endpoint handler.",
                        "HIGH",
                        List.of()
                )),
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/CustomerController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        false,
                        "Endpoint handler.",
                        "// file: src/main/java/com/example/CustomerController.java\npublic CustomerResponse getCustomer() {}",
                        0,
                        List.of()
                )),
                List.of(),
                List.of(),
                new FlowExplorerContextCoverage(
                        true,
                        1,
                        1,
                        1,
                        1,
                        0,
                        1,
                        103,
                        false,
                        0,
                        0,
                        false,
                        false,
                        false,
                        "HIGH"
                )
        );
    }

    private static ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
