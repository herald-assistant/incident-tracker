package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;
import pl.mkn.incidenttracker.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;
import pl.mkn.incidenttracker.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerCopilotRuntimePreparationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldAllowOnlyFlowExplorerGitLabOperationalContextAndFeedbackTools() {
        var gitLabTool = tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK);
        var operationalContextTool = tool(OperationalContextToolNames.SEARCH);
        var databaseTool = tool(DatabaseToolNames.COUNT_ROWS);
        var elasticTool = tool(ElasticToolNames.SEARCH_LOGS_BY_CORRELATION_ID);
        var feedbackTool = tool(CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK);

        var policy = FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of(
                databaseTool,
                gitLabTool,
                elasticTool,
                operationalContextTool,
                feedbackTool
        ));

        assertEquals(
                List.of(
                        GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                        OperationalContextToolNames.SEARCH,
                        CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK
                ),
                policy.availableToolNames()
        );
        assertEquals(List.of(gitLabTool, operationalContextTool, feedbackTool), policy.enabledTools());
        assertTrue(policy.localWorkspaceAccessBlocked());
        assertTrue(policy.gitLabToolsRegistered());
        assertTrue(policy.operationalContextToolsRegistered());
        assertTrue(policy.databaseToolsRegistered());
        assertTrue(policy.elasticToolsRegistered());
        assertTrue(policy.gitLabToolsEnabled());
        assertTrue(policy.operationalContextToolsEnabled());
        assertTrue(policy.toolFeedbackEnabled());
        assertFalse(policy.databaseToolsEnabled());
        assertFalse(policy.elasticToolsEnabled());
    }

    @Test
    void shouldBuildHiddenToolContextForSelectedEndpointScope() {
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
        assertEquals("job-123", hiddenContext.get(AgentToolContextKeys.CORRELATION_ID));
        assertEquals("platform/backend", hiddenContext.get(AgentToolContextKeys.GITLAB_GROUP));
        assertEquals("feature/FLOW-42", hiddenContext.get(AgentToolContextKeys.GITLAB_BRANCH));
        assertEquals("crm-service", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.SYSTEM_ID));
        assertEquals("CRM Service", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.SYSTEM_NAME));
        assertEquals("GET:/api/customers/{id}", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.ENDPOINT_ID));
        assertEquals("GET", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.HTTP_METHOD));
        assertEquals("/api/customers/{id}", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.ENDPOINT_PATH));
        assertEquals("crm-service", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.PROJECT_NAME));
        assertEquals("TEST_PREPARATION", hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.DOCUMENTATION_PRESET));
        assertEquals(List.of("BUSINESS_FLOW"), hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.FOCUS_AREAS));
        assertEquals(
                List.of("flow-explorer/context-snapshot.json", "flow-explorer/snippet-cards.md"),
                hiddenContext.get(FlowExplorerCopilotHiddenToolContextKeys.ARTIFACT_NAMES)
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
                request().aiOptions()
        );

        assertEquals("flow-explorer-job-123", request.sessionId());
        assertEquals(List.of(tool), request.tools());
        assertEquals(List.of(GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT), request.availableToolNames());
        assertSkillDirectories(request.skillDirectories());
        assertTrue(request.effectiveAvailableToolNames().contains("skill"));
        assertEquals("gpt-5.4", request.modelSelection().model());
        assertEquals("medium", request.modelSelection().reasoningEffort());
        assertEquals(
                "Use only the inline Flow Explorer artifacts and the explicitly enabled Flow Explorer tools for this session.",
                request.deniedToolUseMessage()
        );
    }

    @Test
    void shouldBuildFollowUpSessionConfigWithoutResultContractSkill() {
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
        assertFalse(request.skillDirectories().stream().anyMatch(directory -> directory.endsWith("flow-explorer-result-contract")));
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

        when(toolFactory.createToolDefinitions(contextCaptor.capture()))
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
        assertSame(assembly.toolSessionContext(), contextCaptor.getValue());
        assertEquals(
                List.of(
                        GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                        CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK
                ),
                runRequest.sessionConfigRequest().availableToolNames()
        );
        assertSkillDirectories(runRequest.sessionConfigRequest().skillDirectories());
        assertFalse(assembly.toolAccessPolicy().databaseToolsEnabled());

        verify(toolFactory).createToolDefinitions(assembly.toolSessionContext());
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

        when(toolFactory.createToolDefinitions(contextCaptor.capture()))
                .thenReturn(List.of(gitLabTool));

        var assembly = assembler.assembleFollowUp(
                "follow-up-123",
                request(),
                contextSnapshot(),
                preparation()
        );
        var runRequest = assembly.runRequest();

        assertEquals("follow-up-123", runRequest.runReference());
        assertEquals("Flow Explorer canonical prompt", runRequest.prompt());
        assertSkillDirectories(
                runRequest.sessionConfigRequest().skillDirectories(),
                FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames()
        );
        assertFalse(runRequest.sessionConfigRequest()
                .skillDirectories()
                .stream()
                .anyMatch(directory -> directory.endsWith("flow-explorer-result-contract")));

        verify(toolFactory).createToolDefinitions(assembly.toolSessionContext());
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
        assertEquals(expectedSkillNames.size(), skillDirectories.size());

        var skillDirectoryNames = skillDirectories.stream()
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();

        assertEquals(expectedSkillNames, skillDirectoryNames);
        assertFalse(skillDirectoryNames.stream().anyMatch(name -> name.startsWith("incident-")));
    }

    private static FlowExplorerJobStartRequest request() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerDocumentationPreset.TEST_PREPARATION,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW),
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
