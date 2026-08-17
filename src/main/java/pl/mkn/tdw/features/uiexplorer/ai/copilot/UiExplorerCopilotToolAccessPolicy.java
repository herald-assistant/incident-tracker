package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;

import java.util.List;
import java.util.Set;

public record UiExplorerCopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean fallbackRequired,
        boolean fallbackAvailable
) {

    private static final Set<String> FALLBACK_TOOLS = Set.of(
            GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
    );

    public UiExplorerCopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
    }

    public static UiExplorerCopilotToolAccessPolicy fromRegisteredTools(
            List<ToolDefinition> registeredTools,
            boolean fallbackRequired
    ) {
        var enabled = (registeredTools != null ? registeredTools : List.<ToolDefinition>of()).stream()
                .filter(tool -> FALLBACK_TOOLS.contains(tool.name()))
                .toList();
        var names = enabled.stream().map(ToolDefinition::name).toList();
        var available = names.contains(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES)
                && (names.contains(GitLabToolNames.READ_REPOSITORY_FILE)
                || names.contains(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK));
        return new UiExplorerCopilotToolAccessPolicy(enabled, names, fallbackRequired, available);
    }
}
