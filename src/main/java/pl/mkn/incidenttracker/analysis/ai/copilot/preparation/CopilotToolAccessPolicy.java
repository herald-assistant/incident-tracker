package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record CopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean localWorkspaceAccessBlocked,
        boolean elasticToolsRegistered,
        boolean gitLabToolsRegistered,
        boolean databaseToolsRegistered,
        boolean elasticDataAttached,
        boolean gitLabDataAttached
) {

    public CopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
    }

    public static CopilotToolAccessPolicy from(
            AnalysisAiAnalysisRequest request,
            List<ToolDefinition> registeredTools
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        List<AnalysisEvidenceSection> evidenceSections = request != null && request.evidenceSections() != null
                ? request.evidenceSections()
                : List.of();
        var elasticDataAttached = !ElasticLogEvidenceView.from(evidenceSections).isEmpty();
        var gitLabDataAttached = GitLabResolvedCodeEvidenceView.from(evidenceSections).hasItems();
        var elasticToolsRegistered = hasToolPrefix(tools, "elastic_");
        var gitLabToolsRegistered = hasToolPrefix(tools, "gitlab_");
        var databaseToolsRegistered = hasToolPrefix(tools, "db_");
        var enabledTools = tools.stream()
                .filter(tool -> isEnabled(tool.name(), elasticDataAttached, gitLabDataAttached))
                .toList();
        var availableToolNames = enabledTools.stream()
                .map(ToolDefinition::name)
                .toList();

        return new CopilotToolAccessPolicy(
                enabledTools,
                availableToolNames,
                true,
                elasticToolsRegistered,
                gitLabToolsRegistered,
                databaseToolsRegistered,
                elasticDataAttached,
                gitLabDataAttached
        );
    }

    public List<String> enabledCapabilityGroups() {
        var groups = new LinkedHashSet<String>();
        if (elasticToolsEnabled()) {
            groups.add("elasticsearch");
        }
        if (gitLabToolsEnabled()) {
            groups.add("gitlab");
        }
        if (databaseToolsEnabled()) {
            groups.add("database");
        }
        return List.copyOf(groups);
    }

    public List<Map<String, String>> disabledCapabilityGroups() {
        var groups = new ArrayList<Map<String, String>>();
        if (elasticToolsRegistered && elasticDataAttached) {
            groups.add(Map.of(
                    "name", "elasticsearch",
                    "reason", "Equivalent Elasticsearch log data is already attached to the session."
            ));
        }
        if (gitLabToolsRegistered && gitLabDataAttached) {
            groups.add(Map.of(
                    "name", "gitlab",
                    "reason", "Equivalent deterministic GitLab code evidence is already attached to the session."
            ));
        }
        return List.copyOf(groups);
    }

    public boolean elasticToolsEnabled() {
        return hasAvailableToolPrefix("elastic_");
    }

    public boolean gitLabToolsEnabled() {
        return hasAvailableToolPrefix("gitlab_");
    }

    public boolean databaseToolsEnabled() {
        return hasAvailableToolPrefix("db_");
    }

    private boolean hasAvailableToolPrefix(String prefix) {
        return availableToolNames.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private static boolean hasToolPrefix(List<ToolDefinition> tools, String prefix) {
        return tools.stream().map(ToolDefinition::name).anyMatch(name -> name.startsWith(prefix));
    }

    private static boolean isEnabled(String toolName, boolean elasticDataAttached, boolean gitLabDataAttached) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (toolName.startsWith("elastic_")) {
            return !elasticDataAttached;
        }
        if (toolName.startsWith("gitlab_")) {
            return !gitLabDataAttached;
        }
        return true;
    }
}
