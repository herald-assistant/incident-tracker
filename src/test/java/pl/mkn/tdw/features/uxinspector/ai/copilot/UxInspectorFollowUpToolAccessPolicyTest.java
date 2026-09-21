package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorToolNames;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class UxInspectorFollowUpToolAccessPolicyTest {
    @Test
    void shouldKeepResearchToolsAndRemoveReportMutationTools() {
        var registered = new ArrayList<ToolDefinition>();
        UxInspectorToolNames.ALL.forEach(name -> registered.add(tool(name)));
        registered.add(tool(GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE));
        registered.add(tool(GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE));
        registered.add(tool(GitLabToolNames.LIST_REPOSITORY_TREE));
        registered.add(tool(GitLabToolNames.LIST_REPOSITORY_FILES));
        registered.add(tool(GitLabToolNames.SEARCH_REPOSITORY_FILES));
        registered.add(tool(GitLabToolNames.READ_REPOSITORY_FILE));
        registered.add(tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK));
        registered.add(tool(GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE));
        CopilotReportToolNames.allToolNames().forEach(name -> registered.add(tool(name)));

        var policy = UxInspectorCopilotToolAccessPolicy.forFollowUp(registered);

        assertThat(policy.followUpResearchAvailable()).isTrue();
        assertThat(policy.availableToolNames()).containsAll(UxInspectorToolNames.ALL);
        assertThat(policy.availableToolNames()).doesNotContainAnyElementsOf(CopilotReportToolNames.allToolNames());
    }

    private static ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(name, name, Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok")));
    }
}
