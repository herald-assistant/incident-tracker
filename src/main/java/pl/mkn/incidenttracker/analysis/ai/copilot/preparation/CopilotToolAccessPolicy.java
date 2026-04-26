package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.ElasticEvidenceCoverage;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.GitLabEvidenceCoverage;

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
        boolean elasticDataAttached,
        boolean gitLabDataAttached,
        CopilotEvidenceCoverageReport evidenceCoverage
) {

    private static final Set<String> FOCUSED_GITLAB_TOOLS = Set.of(
            "gitlab_find_class_references",
            "gitlab_find_flow_context",
            "gitlab_read_repository_file_chunk",
            "gitlab_read_repository_file_chunks",
            "gitlab_read_repository_file_outline"
    );

    private static final Set<String> DB_DISCOVERY_TOOLS = Set.of(
            "db_get_scope",
            "db_find_tables",
            "db_find_columns",
            "db_describe_table",
            "db_find_relationships"
    );

    public CopilotToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        evidenceCoverage = evidenceCoverage != null ? evidenceCoverage : CopilotEvidenceCoverageReport.empty();
    }

    public static CopilotToolAccessPolicy from(
            AnalysisAiAnalysisRequest request,
            List<ToolDefinition> registeredTools
    ) {
        return from(request, registeredTools, new CopilotEvidenceCoverageEvaluator().evaluate(request));
    }

    public static CopilotToolAccessPolicy from(
            AnalysisAiAnalysisRequest request,
            List<ToolDefinition> registeredTools,
            CopilotEvidenceCoverageReport evidenceCoverage
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        var coverage = evidenceCoverage != null ? evidenceCoverage : CopilotEvidenceCoverageReport.empty();
        var elasticDataAttached = coverage.elastic() != ElasticEvidenceCoverage.NONE;
        var gitLabDataAttached = coverage.gitLab() != GitLabEvidenceCoverage.NONE;
        var elasticToolsRegistered = hasToolPrefix(tools, "elastic_");
        var gitLabToolsRegistered = hasToolPrefix(tools, "gitlab_");
        var databaseToolsRegistered = hasToolPrefix(tools, "db_");
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
                elasticDataAttached,
                gitLabDataAttached,
                coverage
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
                    "reason", "GitLab coverage is %s and no code or flow evidence gap requires GitLab tools."
                            .formatted(evidenceCoverage.gitLab())
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
        return hasAvailableToolPrefix("elastic_");
    }

    public boolean gitLabToolsEnabled() {
        return hasAvailableToolPrefix("gitlab_");
    }

    public boolean databaseToolsEnabled() {
        return hasAvailableToolPrefix("db_");
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

    private static boolean isEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (toolName.startsWith("elastic_")) {
            return evidenceCoverage.elasticNeedsTooling();
        }
        if (toolName.startsWith("gitlab_")) {
            return gitLabToolEnabled(toolName, evidenceCoverage);
        }
        if (toolName.startsWith("db_")) {
            return databaseToolEnabled(toolName, evidenceCoverage);
        }
        return true;
    }

    private static boolean gitLabToolEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if (!evidenceCoverage.gitLabNeedsTooling()) {
            return false;
        }
        if (evidenceCoverage.gitLab() == GitLabEvidenceCoverage.NONE) {
            return true;
        }
        return FOCUSED_GITLAB_TOOLS.contains(toolName);
    }

    private static boolean databaseToolEnabled(String toolName, CopilotEvidenceCoverageReport evidenceCoverage) {
        if ("db_execute_readonly_sql".equals(toolName)) {
            return false;
        }
        if (evidenceCoverage.databaseNeedsTooling()) {
            return true;
        }
        return evidenceCoverage.databaseDiscoveryOnly() && DB_DISCOVERY_TOOLS.contains(toolName);
    }
}
