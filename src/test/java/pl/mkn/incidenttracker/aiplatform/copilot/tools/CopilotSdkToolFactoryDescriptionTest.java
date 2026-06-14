package pl.mkn.incidenttracker.aiplatform.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.integrations.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabMcpTools;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolFactory;
import static pl.mkn.incidenttracker.testsupport.copilot.CopilotTestFixtures.toolEvidenceSessionStore;

class CopilotSdkToolFactoryDescriptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDecorateCopilotFacingToolDescriptions() {
        var factory = toolFactory(
                List.of(gitLabToolProvider()),
                objectMapper,
                toolEvidenceSessionStore(objectMapper),
                new pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetPolicy(
                        new pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry(
                                new pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties()
                        )
                )
        );

        var descriptionsByName = factory.createToolDefinitions(sessionContext()).stream()
                .collect(Collectors.toMap(ToolDefinition::name, ToolDefinition::description));

        assertTrue(descriptionsByName.get("gitlab_read_repository_file").contains("Expensive."));
        assertTrue(descriptionsByName.get("gitlab_read_repository_file")
                .contains("Prefer gitlab_read_repository_file_chunk"));
        assertTrue(descriptionsByName.get("gitlab_list_available_repositories")
                .contains("Use returned projectName values"));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("Use when project or file is unclear."));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("including supporting repositories"));
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

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }
}
