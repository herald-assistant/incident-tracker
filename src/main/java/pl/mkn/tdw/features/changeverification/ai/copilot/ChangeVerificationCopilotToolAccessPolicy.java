package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;

import java.util.List;
import java.util.Set;

record ChangeVerificationCopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean gitLabToolsRegistered,
        boolean operationalContextToolsRegistered
) {

    private static final Set<String> TOOL_ALLOWLIST = Set.of(
            GitLabToolNames.LIST_REPOSITORY_ENDPOINTS,
            GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT,
            GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT,
            GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
            GitLabToolNames.READ_JAVA_METHOD_SLICE,
            GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
            GitLabToolNames.FIND_CLASS_REFERENCES,
            GitLabToolNames.FIND_FLOW_CONTEXT,
            OperationalContextToolNames.GET_SCOPE,
            OperationalContextToolNames.LIST_ENTITIES,
            OperationalContextToolNames.SEARCH,
            OperationalContextToolNames.GET_ENTITY
    );

    ChangeVerificationCopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
    }

    static ChangeVerificationCopilotToolAccessPolicy fromRegisteredTools(List<ToolDefinition> registeredTools) {
        var tools = registeredTools != null ? List.copyOf(registeredTools) : List.<ToolDefinition>of();
        var enabledTools = tools.stream()
                .filter(tool -> TOOL_ALLOWLIST.contains(tool.name()))
                .toList();

        return new ChangeVerificationCopilotToolAccessPolicy(
                enabledTools,
                enabledTools.stream().map(ToolDefinition::name).toList(),
                hasToolPrefix(tools, GitLabToolNames.PREFIX),
                hasToolPrefix(tools, OperationalContextToolNames.PREFIX)
        );
    }

    boolean gitLabToolsEnabled() {
        return availableToolNames.stream().anyMatch(name -> name != null && name.startsWith(GitLabToolNames.PREFIX));
    }

    boolean operationalContextToolsEnabled() {
        return availableToolNames.stream().anyMatch(name -> name != null && name.startsWith(OperationalContextToolNames.PREFIX));
    }

    private static boolean hasToolPrefix(List<ToolDefinition> tools, String prefix) {
        return tools.stream().map(ToolDefinition::name).anyMatch(name -> name != null && name.startsWith(prefix));
    }
}
