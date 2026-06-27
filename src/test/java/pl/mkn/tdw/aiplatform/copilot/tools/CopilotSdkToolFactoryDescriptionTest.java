package pl.mkn.tdw.aiplatform.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabMcpTools;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkToolFactoryDescriptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final CopilotToolDescriptionContext INCIDENT_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");
    private static final CopilotToolDescriptionContext FLOW_EXPLORER_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    @Test
    void shouldDecorateCopilotFacingToolDescriptions() {
        var factory = toolFactory(
                List.of(gitLabToolProvider()),
                objectMapper,
                toolEvidenceSessionStore(objectMapper),
                new pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetPolicy(
                        new pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry(
                                new pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties()
                        )
                )
        );

        var descriptionsByName = factory.createToolDefinitions(
                        sessionContext(),
                        INCIDENT_DESCRIPTION_CONTEXT
                ).stream()
                .collect(Collectors.toMap(ToolDefinition::name, ToolDefinition::description));

        assertTrue(descriptionsByName.get("gitlab_read_repository_file").contains("Expensive."));
        assertTrue(descriptionsByName.get("gitlab_read_repository_file")
                .contains("Prefer gitlab_read_java_method_slice"));
        assertTrue(descriptionsByName.get("gitlab_read_repository_file")
                .contains("Pass branchRef explicitly"));
        assertTrue(descriptionsByName.get("gitlab_read_repository_files_by_path")
                .contains("grounded file list"));
        assertTrue(descriptionsByName.get("gitlab_list_available_repositories")
                .contains("Use returned projectName values"));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("Use when project or file is unclear."));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("including supporting repositories"));
    }

    @Test
    void shouldPassDescriptionContextToDescriptionCustomizers() {
        var capturedContext = new AtomicReference<CopilotToolDescriptionContext>();
        CopilotToolDescriptionCustomizer customizer = (descriptionContext, toolName, description) -> {
            capturedContext.set(descriptionContext);
            return description + "\ncustomized for " + descriptionContext.profileId();
        };
        var factory = new CopilotSdkToolFactory(
                List.of(gitLabToolProvider()),
                objectMapper,
                List.of(customizer),
                mock(CopilotToolInvocationHandler.class)
        );
        var sessionContext = flowExplorerSessionContext();

        var descriptionsByName = factory.createToolDefinitions(
                        sessionContext,
                        FLOW_EXPLORER_DESCRIPTION_CONTEXT
                ).stream()
                .collect(Collectors.toMap(ToolDefinition::name, ToolDefinition::description));

        assertSame(FLOW_EXPLORER_DESCRIPTION_CONTEXT, capturedContext.get());
        assertTrue(descriptionsByName.get("gitlab_read_repository_file")
                .contains("customized for flow-explorer"));
    }

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "CRM/runtime"
        );
    }

    private CopilotToolSessionContext flowExplorerSessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "flow-explorer-run-1",
                Map.of()
        );
    }

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort(), gitLabProperties("CRM/runtime")))
                .build();
    }

    private GitLabProperties gitLabProperties(String group) {
        var properties = new GitLabProperties();
        properties.setGroup(group);
        return properties;
    }
}
