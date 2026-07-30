package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.List;
import java.util.Set;

public record RuntimeConfigurationCopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        RuntimeConfigurationVerificationMode mode
) {

    private static final Set<String> DEEP_TOOLS = Set.of(
            OperationalContextToolNames.GET_ENTITY,
            GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_JAVA_METHOD_SLICE
    );

    public RuntimeConfigurationCopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        mode = mode != null ? mode : RuntimeConfigurationVerificationMode.BASIC;
    }

    public static RuntimeConfigurationCopilotToolAccessPolicy fromRegisteredTools(
            List<ToolDefinition> registeredTools,
            RuntimeConfigurationVerificationMode mode
    ) {
        var resolvedMode = mode != null ? mode : RuntimeConfigurationVerificationMode.BASIC;
        var tools = registeredTools != null ? List.copyOf(registeredTools) : List.<ToolDefinition>of();
        var enabled = tools.stream()
                .filter(tool -> isWritableReportTool(tool.name())
                        || resolvedMode == RuntimeConfigurationVerificationMode.DEEP
                        && DEEP_TOOLS.contains(tool.name()))
                .toList();
        return new RuntimeConfigurationCopilotToolAccessPolicy(
                enabled,
                enabled.stream().map(ToolDefinition::name).toList(),
                resolvedMode
        );
    }

    private static boolean isWritableReportTool(String toolName) {
        return CopilotReportToolNames.GET_CURRENT.equals(toolName)
                || CopilotReportToolNames.UPSERT_SECTION.equals(toolName);
    }
}
