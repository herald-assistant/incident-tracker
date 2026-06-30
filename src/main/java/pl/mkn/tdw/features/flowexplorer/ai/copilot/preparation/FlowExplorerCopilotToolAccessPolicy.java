package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;

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
            GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
            GitLabToolNames.READ_JAVA_METHOD_SLICE,
            GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
            GitLabToolNames.FIND_FLOW_CONTEXT,
            OperationalContextToolNames.GET_SCOPE,
            OperationalContextToolNames.LIST_ENTITIES,
            OperationalContextToolNames.SEARCH,
            OperationalContextToolNames.GET_ENTITY,
            CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
            CopilotReportToolNames.GET_CURRENT,
            CopilotReportToolNames.UPSERT_SECTION,
            CopilotReportToolNames.UPDATE_HEADER,
            CopilotReportToolNames.UPDATE_META
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

    public boolean reportToolsEnabled() {
        return availableToolNames.stream().anyMatch(CopilotReportToolNames::isReportTool);
    }

    private boolean hasAvailableToolPrefix(String prefix) {
        return availableToolNames.stream().anyMatch(name -> name != null && name.startsWith(prefix));
    }

    private static boolean hasToolPrefix(List<ToolDefinition> tools, String prefix) {
        return tools.stream().map(ToolDefinition::name).anyMatch(name -> name != null && name.startsWith(prefix));
    }
}
