package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;
import pl.mkn.incidenttracker.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;
import pl.mkn.incidenttracker.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;

import java.util.List;
import java.util.Set;

public record FlowExplorerCopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean localWorkspaceAccessBlocked,
        boolean gitLabToolsRegistered,
        boolean operationalContextToolsRegistered,
        boolean databaseToolsRegistered,
        boolean elasticToolsRegistered
) {

    private static final Set<String> FLOW_EXPLORER_TOOL_ALLOWLIST = Set.of(
            GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
            GitLabToolNames.LIST_REPOSITORY_ENDPOINTS,
            GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
            GitLabToolNames.FIND_FLOW_CONTEXT,
            OperationalContextToolNames.GET_SCOPE,
            OperationalContextToolNames.LIST_ENTITIES,
            OperationalContextToolNames.SEARCH,
            OperationalContextToolNames.GET_ENTITY,
            CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK
    );

    public FlowExplorerCopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
    }

    public static FlowExplorerCopilotToolAccessPolicy fromRegisteredTools(List<ToolDefinition> registeredTools) {
        var tools = registeredTools != null ? List.copyOf(registeredTools) : List.<ToolDefinition>of();
        var enabledTools = tools.stream()
                .filter(tool -> FLOW_EXPLORER_TOOL_ALLOWLIST.contains(tool.name()))
                .toList();

        return new FlowExplorerCopilotToolAccessPolicy(
                enabledTools,
                enabledTools.stream().map(ToolDefinition::name).toList(),
                true,
                hasToolPrefix(tools, GitLabToolNames.PREFIX),
                hasToolPrefix(tools, OperationalContextToolNames.PREFIX),
                hasToolPrefix(tools, DatabaseToolNames.PREFIX),
                hasToolPrefix(tools, ElasticToolNames.PREFIX)
        );
    }

    public boolean gitLabToolsEnabled() {
        return hasAvailableToolPrefix(GitLabToolNames.PREFIX);
    }

    public boolean operationalContextToolsEnabled() {
        return hasAvailableToolPrefix(OperationalContextToolNames.PREFIX);
    }

    public boolean databaseToolsEnabled() {
        return hasAvailableToolPrefix(DatabaseToolNames.PREFIX);
    }

    public boolean elasticToolsEnabled() {
        return hasAvailableToolPrefix(ElasticToolNames.PREFIX);
    }

    public boolean toolFeedbackEnabled() {
        return availableToolNames.stream().anyMatch(CopilotToolFeedbackToolNames::isFeedbackTool);
    }

    private boolean hasAvailableToolPrefix(String prefix) {
        return availableToolNames.stream().anyMatch(name -> name != null && name.startsWith(prefix));
    }

    private static boolean hasToolPrefix(List<ToolDefinition> tools, String prefix) {
        return tools.stream().map(ToolDefinition::name).anyMatch(name -> name != null && name.startsWith(prefix));
    }
}
