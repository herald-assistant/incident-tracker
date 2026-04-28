package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.GitLabEvidenceCoverage;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolNames;
import pl.mkn.incidenttracker.analysis.mcp.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolNames;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CopilotToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean localWorkspaceAccessBlocked,
        boolean elasticToolsRegistered,
        boolean gitLabToolsRegistered,
        boolean databaseToolsRegistered,
        CopilotEvidenceCoverageReport evidenceCoverage
) {

    private static final Set<String> FOCUSED_GITLAB_TOOLS = Set.of(
            GitLabToolNames.FIND_CLASS_REFERENCES,
            GitLabToolNames.FIND_FLOW_CONTEXT,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE
    );

    private static final Set<String> DB_DISCOVERY_TOOLS = Set.of(
            DatabaseToolNames.GET_SCOPE,
            DatabaseToolNames.FIND_TABLES,
            DatabaseToolNames.FIND_COLUMNS,
            DatabaseToolNames.DESCRIBE_TABLE,
            DatabaseToolNames.FIND_RELATIONSHIPS
    );

    public CopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        evidenceCoverage = evidenceCoverage != null ? evidenceCoverage : CopilotEvidenceCoverageReport.empty();
    }

    public static CopilotToolAccessPolicy empty() {
        return new CopilotToolAccessPolicy(
                List.of(),
                List.of(),
                true,
                false,
                false,
                false,
                CopilotEvidenceCoverageReport.empty()
        );
    }

    public static CopilotToolAccessPolicy fromCoverage(
            List<ToolDefinition> registeredTools,
            CopilotEvidenceCoverageReport evidenceCoverage
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        var coverage = evidenceCoverage != null ? evidenceCoverage : CopilotEvidenceCoverageReport.empty();
        var elasticToolsRegistered = hasToolPrefix(tools, ElasticToolNames.PREFIX);
        var gitLabToolsRegistered = hasToolPrefix(tools, GitLabToolNames.PREFIX);
        var databaseToolsRegistered = hasToolPrefix(tools, DatabaseToolNames.PREFIX);
        var enabledTools = tools.stream()
                .filter(tool -> isEnabled(tool.name(), coverage))
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
                coverage
        );
    }

    public static CopilotToolAccessPolicy fromFollowUpSession(
            List<ToolDefinition> registeredTools,
            boolean environmentResolved,
            boolean gitLabScopeResolved
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        var elasticToolsRegistered = hasToolPrefix(tools, ElasticToolNames.PREFIX);
        var gitLabToolsRegistered = hasToolPrefix(tools, GitLabToolNames.PREFIX);
        var databaseToolsRegistered = hasToolPrefix(tools, DatabaseToolNames.PREFIX);
        var enabledTools = tools.stream()
                .filter(tool -> isFollowUpEnabled(tool.name(), environmentResolved, gitLabScopeResolved))
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
                CopilotEvidenceCoverageReport.empty()
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
        if (elasticToolsRegistered && !elasticToolsEnabled()) {
            groups.add(Map.of(
                    "name", "elasticsearch",
                    "reason", "Elasticsearch coverage is %s and no log evidence gap requires additional log tools."
                            .formatted(evidenceCoverage.elastic())
            ));
        }
        if (gitLabToolsRegistered && !gitLabToolsEnabled()) {
            groups.add(Map.of(
                    "name", "gitlab",
                    "reason", gitLabDisabledReason()
            ));
        }
        if (databaseToolsRegistered && !databaseToolsEnabled()) {
            groups.add(Map.of(
                    "name", "database",
                    "reason", databaseDisabledReason()
            ));
        }
        return List.copyOf(groups);
    }

    public boolean elasticToolsEnabled() {
        return hasAvailableToolPrefix(ElasticToolNames.PREFIX);
    }

    public boolean gitLabToolsEnabled() {
        return hasAvailableToolPrefix(GitLabToolNames.PREFIX);
    }

    public boolean databaseToolsEnabled() {
        return hasAvailableToolPrefix(DatabaseToolNames.PREFIX);
    }

    private boolean hasAvailableToolPrefix(String prefix) {
        return availableToolNames.stream().anyMatch(name -> name != null && name.startsWith(prefix));
    }

    private static boolean hasToolPrefix(List<ToolDefinition> tools, String prefix) {
        return tools.stream().map(ToolDefinition::name).anyMatch(name -> name != null && name.startsWith(prefix));
    }

    private String databaseDisabledReason() {
        if (!evidenceCoverage.environmentResolved()) {
            return "Database tools require a resolved runtime environment.";
        }
        return "Data diagnostic need is %s, so DB tools are not enabled for this session."
                .formatted(evidenceCoverage.dataDiagnosticNeed());
    }

    private String gitLabDisabledReason() {
        if (evidenceCoverage.affectedFunctionGitLabRecommended()) {
            return "GitLab affectedFunction grounding is recommended, but no eligible focused GitLab tools are enabled in this session.";
        }
        if (evidenceCoverage.databaseCodeGroundingNeedsTooling()) {
            return "GitLab DB code grounding is recommended, but no eligible focused GitLab tools are enabled in this session.";
        }
        return "GitLab coverage is %s and no code or flow evidence gap requires GitLab tools."
                .formatted(evidenceCoverage.gitLab());
    }

    private static boolean isEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (toolName.startsWith(ElasticToolNames.PREFIX)) {
            return evidenceCoverage.elasticNeedsTooling();
        }
        if (toolName.startsWith(GitLabToolNames.PREFIX)) {
            return gitLabToolEnabled(toolName, evidenceCoverage);
        }
        if (toolName.startsWith(DatabaseToolNames.PREFIX)) {
            return databaseToolEnabled(toolName, evidenceCoverage);
        }
        return true;
    }

    private static boolean gitLabToolEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if (evidenceCoverage.databaseCodeGroundingNeedsTooling() && FOCUSED_GITLAB_TOOLS.contains(toolName)) {
            return true;
        }
        if (!evidenceCoverage.gitLabNeedsTooling()) {
            return false;
        }
        if (evidenceCoverage.gitLab() == GitLabEvidenceCoverage.NONE) {
            return true;
        }
        return FOCUSED_GITLAB_TOOLS.contains(toolName);
    }

    private static boolean databaseToolEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if (DatabaseToolNames.EXECUTE_READONLY_SQL.equals(toolName)) {
            return false;
        }
        if (evidenceCoverage.databaseNeedsTooling()) {
            return true;
        }
        return evidenceCoverage.databaseDiscoveryOnly() && DB_DISCOVERY_TOOLS.contains(toolName);
    }

    private static boolean isFollowUpEnabled(
            String toolName,
            boolean environmentResolved,
            boolean gitLabScopeResolved
    ) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (toolName.startsWith(ElasticToolNames.PREFIX)) {
            return true;
        }
        if (toolName.startsWith(GitLabToolNames.PREFIX)) {
            return gitLabScopeResolved;
        }
        if (toolName.startsWith(DatabaseToolNames.PREFIX)) {
            return environmentResolved && !DatabaseToolNames.EXECUTE_READONLY_SQL.equals(toolName);
        }
        return true;
    }
}
