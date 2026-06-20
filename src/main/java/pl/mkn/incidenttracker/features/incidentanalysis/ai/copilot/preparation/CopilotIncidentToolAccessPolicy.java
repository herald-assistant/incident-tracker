package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageReport;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage.IncidentGitLabEvidenceCoverage;
import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;
import pl.mkn.incidenttracker.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;
import pl.mkn.incidenttracker.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CopilotIncidentToolAccessPolicy(
        List<ToolDefinition> enabledTools,
        List<String> availableToolNames,
        boolean localWorkspaceAccessBlocked,
        boolean elasticToolsRegistered,
        boolean gitLabToolsRegistered,
        boolean databaseToolsRegistered,
        boolean operationalContextToolsRegistered,
        CopilotIncidentEvidenceCoverageReport evidenceCoverage
) {

    private static final Set<String> FOCUSED_GITLAB_TOOLS = Set.of(
            GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
            GitLabToolNames.FIND_CLASS_REFERENCES,
            GitLabToolNames.FIND_FLOW_CONTEXT,
            GitLabToolNames.READ_JAVA_METHOD_SLICE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
            GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE
    );

    private static final Set<String> DB_DISCOVERY_TOOLS = Set.of(
            DatabaseToolNames.GET_SCOPE,
            DatabaseToolNames.FIND_TABLES,
            DatabaseToolNames.FIND_COLUMNS,
            DatabaseToolNames.DESCRIBE_TABLE,
            DatabaseToolNames.FIND_RELATIONSHIPS
    );

    public CopilotIncidentToolAccessPolicy {
        enabledTools = enabledTools != null ? List.copyOf(enabledTools) : List.of();
        availableToolNames = availableToolNames != null ? List.copyOf(availableToolNames) : List.of();
        evidenceCoverage = evidenceCoverage != null ? evidenceCoverage : CopilotIncidentEvidenceCoverageReport.empty();
    }

    public CopilotIncidentToolAccessPolicy(
            List<ToolDefinition> enabledTools,
            List<String> availableToolNames,
            boolean localWorkspaceAccessBlocked,
            boolean elasticToolsRegistered,
            boolean gitLabToolsRegistered,
            boolean databaseToolsRegistered,
            CopilotIncidentEvidenceCoverageReport evidenceCoverage
    ) {
        this(
                enabledTools,
                availableToolNames,
                localWorkspaceAccessBlocked,
                elasticToolsRegistered,
                gitLabToolsRegistered,
                databaseToolsRegistered,
                false,
                evidenceCoverage
        );
    }

    public static CopilotIncidentToolAccessPolicy empty() {
        return new CopilotIncidentToolAccessPolicy(
                List.of(),
                List.of(),
                true,
                false,
                false,
                false,
                false,
                CopilotIncidentEvidenceCoverageReport.empty()
        );
    }

    public static CopilotIncidentToolAccessPolicy fromCoverage(
            List<ToolDefinition> registeredTools,
            CopilotIncidentEvidenceCoverageReport evidenceCoverage
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        var coverage = evidenceCoverage != null ? evidenceCoverage : CopilotIncidentEvidenceCoverageReport.empty();
        var elasticToolsRegistered = hasToolPrefix(tools, ElasticToolNames.PREFIX);
        var gitLabToolsRegistered = hasToolPrefix(tools, GitLabToolNames.PREFIX);
        var databaseToolsRegistered = hasToolPrefix(tools, DatabaseToolNames.PREFIX);
        var operationalContextToolsRegistered = hasToolPrefix(tools, OperationalContextToolNames.PREFIX);
        var enabledTools = tools.stream()
                .filter(tool -> isEnabled(tool.name(), coverage))
                .toList();
        var availableToolNames = enabledTools.stream()
                .map(ToolDefinition::name)
                .toList();

        return new CopilotIncidentToolAccessPolicy(
                enabledTools,
                availableToolNames,
                true,
                elasticToolsRegistered,
                gitLabToolsRegistered,
                databaseToolsRegistered,
                operationalContextToolsRegistered,
                coverage
        );
    }

    public static CopilotIncidentToolAccessPolicy fromFollowUpSession(
            List<ToolDefinition> registeredTools,
            boolean environmentResolved,
            boolean gitLabScopeResolved
    ) {
        List<ToolDefinition> tools = registeredTools != null ? List.copyOf(registeredTools) : List.of();
        var elasticToolsRegistered = hasToolPrefix(tools, ElasticToolNames.PREFIX);
        var gitLabToolsRegistered = hasToolPrefix(tools, GitLabToolNames.PREFIX);
        var databaseToolsRegistered = hasToolPrefix(tools, DatabaseToolNames.PREFIX);
        var operationalContextToolsRegistered = hasToolPrefix(tools, OperationalContextToolNames.PREFIX);
        var enabledTools = tools.stream()
                .filter(tool -> isFollowUpEnabled(tool.name(), environmentResolved, gitLabScopeResolved))
                .toList();
        var availableToolNames = enabledTools.stream()
                .map(ToolDefinition::name)
                .toList();

        return new CopilotIncidentToolAccessPolicy(
                enabledTools,
                availableToolNames,
                true,
                elasticToolsRegistered,
                gitLabToolsRegistered,
                databaseToolsRegistered,
                operationalContextToolsRegistered,
                CopilotIncidentEvidenceCoverageReport.empty()
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
        if (operationalContextToolsEnabled()) {
            groups.add("operational-context");
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
        if (operationalContextToolsRegistered && !operationalContextToolsEnabled()) {
            groups.add(Map.of(
                    "name", "operational-context",
                    "reason", operationalContextDisabledReason()
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

    public boolean operationalContextToolsEnabled() {
        return hasAvailableToolPrefix(OperationalContextToolNames.PREFIX);
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

    private String databaseDisabledReason() {
        if (!evidenceCoverage.environmentResolved()) {
            return "Database tools require a resolved runtime environment.";
        }
        return "Data diagnostic need is %s, so DB tools are not enabled for this session."
                .formatted(evidenceCoverage.dataDiagnosticNeed());
    }

    private String gitLabDisabledReason() {
        if (evidenceCoverage.technicalAnalysisGitLabRecommended()) {
            return "GitLab technicalAnalysis grounding is recommended, but no eligible focused GitLab tools are enabled in this session.";
        }
        if (evidenceCoverage.databaseCodeGroundingNeedsTooling()) {
            return "GitLab DB code grounding is recommended, but no eligible focused GitLab tools are enabled in this session.";
        }
        return "GitLab coverage is %s and no code or flow evidence gap requires GitLab tools."
                .formatted(evidenceCoverage.gitLab());
    }

    private String operationalContextDisabledReason() {
        return "Operational context coverage is %s and no context, flow, functional-analysis, technical-analysis or DB code-grounding gap requires catalog tools."
                .formatted(evidenceCoverage.operationalContext());
    }

    private static boolean isEnabled(String toolName, CopilotIncidentEvidenceCoverageReport evidenceCoverage) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (CopilotToolFeedbackToolNames.isFeedbackTool(toolName)) {
            return true;
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
        if (toolName.startsWith(OperationalContextToolNames.PREFIX)) {
            return true;
        }
        return true;
    }

    private static boolean gitLabToolEnabled(String toolName, CopilotIncidentEvidenceCoverageReport evidenceCoverage) {
        if (evidenceCoverage.databaseCodeGroundingNeedsTooling() && FOCUSED_GITLAB_TOOLS.contains(toolName)) {
            return true;
        }
        if (!evidenceCoverage.gitLabNeedsTooling()) {
            return false;
        }
        if (evidenceCoverage.gitLab() == IncidentGitLabEvidenceCoverage.NONE) {
            return true;
        }
        return FOCUSED_GITLAB_TOOLS.contains(toolName);
    }

    private static boolean databaseToolEnabled(String toolName, CopilotIncidentEvidenceCoverageReport evidenceCoverage) {
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
        if (CopilotToolFeedbackToolNames.isFeedbackTool(toolName)) {
            return true;
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
        if (toolName.startsWith(OperationalContextToolNames.PREFIX)) {
            return true;
        }
        return true;
    }
}
