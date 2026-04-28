package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.TestGitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsLogger;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotMetricsProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabMcpTools;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolBridge;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.toolEvidenceCaptureRegistry;

class CopilotSdkToolBridgeDescriptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDecorateCopilotFacingToolDescriptions() {
        var metricsRegistry = new CopilotSessionMetricsRegistry(new CopilotMetricsProperties());
        var bridge = toolBridge(
                List.of(gitLabToolProvider()),
                objectMapper,
                toolEvidenceCaptureRegistry(objectMapper),
                metricsRegistry,
                new CopilotMetricsLogger(new CopilotMetricsProperties(), objectMapper),
                new pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetGuard(
                        new pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetRegistry(
                                new pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget.CopilotToolBudgetProperties()
                        ),
                        metricsRegistry
                )
        );

        var descriptionsByName = bridge.buildToolDefinitions(sessionContext()).stream()
                .collect(Collectors.toMap(ToolDefinition::name, ToolDefinition::description));

        assertTrue(descriptionsByName.get("gitlab_read_repository_file").contains("Expensive."));
        assertTrue(descriptionsByName.get("gitlab_read_repository_file")
                .contains("Prefer gitlab_read_repository_file_chunk"));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("Use when project or file is unclear."));
        assertTrue(descriptionsByName.get("gitlab_search_repository_candidates")
                .contains("including library/shared repositories"));
    }

    private CopilotToolSessionContext sessionContext() {
        return new CopilotToolSessionContext(
                "run-1",
                "analysis-run-1",
                "corr-123",
                "zt01",
                "main",
                "sample/runtime"
        );
    }

    private ToolCallbackProvider gitLabToolProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabMcpTools(new TestGitLabRepositoryPort()))
                .build();
    }
}
