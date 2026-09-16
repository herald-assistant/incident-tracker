package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorToolNames;

import java.util.List;
import java.util.Set;

public record UxInspectorCopilotToolAccessPolicy(List<ToolDefinition> enabledTools, List<String> availableToolNames) {
    private static final Set<String> ALLOWED = Set.of(
            UxInspectorToolNames.LIST_TARGET_CANDIDATES, UxInspectorToolNames.READ_TARGET_SLICE,
            GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
            GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
            GitLabToolNames.LIST_REPOSITORY_TREE,
            GitLabToolNames.LIST_REPOSITORY_FILES,
            GitLabToolNames.SEARCH_REPOSITORY_FILES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            CopilotToolFeedbackToolNames.RECORD_TOOL_FEEDBACK,
            CopilotReportToolNames.GET_CURRENT, CopilotReportToolNames.UPSERT_SECTION,
            CopilotReportToolNames.UPDATE_HEADER, CopilotReportToolNames.UPDATE_META
    );
    public UxInspectorCopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
    }
    public static UxInspectorCopilotToolAccessPolicy from(List<ToolDefinition> registered) {
        var tools = (registered != null ? registered : List.<ToolDefinition>of()).stream()
                .filter(value -> ALLOWED.contains(value.name())).toList();
        return new UxInspectorCopilotToolAccessPolicy(tools, tools.stream().map(ToolDefinition::name).toList());
    }
    public boolean reportToolsAvailable() { return availableToolNames.containsAll(CopilotReportToolNames.allToolNames()); }
    public boolean targetToolsAvailable() { return availableToolNames.containsAll(UxInspectorToolNames.ALL); }
    public boolean sourceToolsAvailable() {
        return availableToolNames.containsAll(List.of(
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.LIST_REPOSITORY_TREE,
                GitLabToolNames.LIST_REPOSITORY_FILES,
                GitLabToolNames.SEARCH_REPOSITORY_FILES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        ));
    }
}
