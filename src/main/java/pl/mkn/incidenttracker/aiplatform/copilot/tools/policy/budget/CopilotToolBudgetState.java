package pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget;

import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;
import pl.mkn.incidenttracker.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;
import pl.mkn.incidenttracker.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.feedback.CopilotToolFeedbackToolNames;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class CopilotToolBudgetState {

    private final String sessionId;
    private final CopilotToolBudgetProperties properties;
    private int totalCalls;
    private int elasticCalls;
    private int gitLabCalls;
    private int gitLabSearchCalls;
    private int gitLabReadFileCalls;
    private int gitLabReadChunkCalls;
    private long gitLabReturnedCharacters;
    private int dbCalls;
    private int dbRawSqlCalls;
    private long dbReturnedCharacters;
    private int operationalContextCalls;
    private long operationalContextReturnedCharacters;
    private int softLimitExceededCount;
    private int deniedToolCalls;
    private int rawSqlAttempts;
    private final List<String> warnings = new ArrayList<>();

    public CopilotToolBudgetState(String sessionId, CopilotToolBudgetProperties properties) {
        this.sessionId = sessionId;
        this.properties = properties;
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized CopilotToolBudgetDecision beforeInvocation(String toolName) {
        if (CopilotToolFeedbackToolNames.isFeedbackTool(toolName)) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        if (!properties.active()) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        if (isRawSqlTool(toolName)) {
            rawSqlAttempts++;
        }

        var exceeded = exceededBeforeInvocation(toolName);
        if (exceeded.isEmpty()) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        if (properties.getMode() == BudgetMode.HARD) {
            deniedToolCalls++;
            warnings.addAll(exceeded);
            return CopilotToolBudgetDecision.denied(sessionId, toolName, exceeded);
        }

        softLimitExceededCount += exceeded.size();
        warnings.addAll(exceeded);
        return CopilotToolBudgetDecision.soft(sessionId, toolName, exceeded);
    }

    public synchronized CopilotToolBudgetDecision afterInvocation(String toolName, String rawResult) {
        if (CopilotToolFeedbackToolNames.isFeedbackTool(toolName)) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        if (!properties.active()) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        var rawResultCharacters = rawResult != null ? rawResult.length() : 0L;
        totalCalls++;

        switch (toolGroup(toolName)) {
            case "elasticsearch" -> elasticCalls++;
            case "gitlab" -> {
                gitLabCalls++;
                gitLabReturnedCharacters += rawResultCharacters;
            }
            case "database" -> {
                dbCalls++;
                dbReturnedCharacters += rawResultCharacters;
            }
            case "operational-context" -> {
                operationalContextCalls++;
                operationalContextReturnedCharacters += rawResultCharacters;
            }
            default -> {
            }
        }

        if (isGitLabSearchTool(toolName)) {
            gitLabSearchCalls++;
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE.equals(toolName)) {
            gitLabReadFileCalls++;
        }
        if (isGitLabChunkTool(toolName)) {
            gitLabReadChunkCalls++;
        }
        if (isRawSqlTool(toolName)) {
            dbRawSqlCalls++;
        }

        var exceeded = exceededAfterInvocation(toolName);
        if (exceeded.isEmpty()) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        softLimitExceededCount += exceeded.size();
        warnings.addAll(exceeded);
        return CopilotToolBudgetDecision.soft(sessionId, toolName, exceeded);
    }

    public synchronized CopilotToolBudgetSnapshot snapshot() {
        return new CopilotToolBudgetSnapshot(
                sessionId,
                totalCalls,
                elasticCalls,
                gitLabCalls,
                gitLabSearchCalls,
                gitLabReadFileCalls,
                gitLabReadChunkCalls,
                gitLabReturnedCharacters,
                dbCalls,
                dbRawSqlCalls,
                dbReturnedCharacters,
                operationalContextCalls,
                operationalContextReturnedCharacters,
                softLimitExceededCount,
                deniedToolCalls,
                rawSqlAttempts,
                warnings
        );
    }

    private List<String> exceededBeforeInvocation(String toolName) {
        var exceeded = new ArrayList<String>();
        addIfLimitExceeded(exceeded, "total tool call budget exceeded", totalCalls + 1, properties.getMaxTotalCalls());

        switch (toolGroup(toolName)) {
            case "elasticsearch" -> addIfLimitExceeded(
                    exceeded,
                    "Elasticsearch tool call budget exceeded",
                    elasticCalls + 1,
                    properties.getMaxElasticCalls()
            );
            case "gitlab" -> {
                addIfLimitExceeded(exceeded, "GitLab tool call budget exceeded", gitLabCalls + 1, properties.getMaxGitlabCalls());
                if (isGitLabSearchTool(toolName)) {
                    addIfLimitExceeded(
                            exceeded,
                            "GitLab search tool call budget exceeded",
                            gitLabSearchCalls + 1,
                            properties.getMaxGitlabSearchCalls()
                    );
                }
                if (GitLabToolNames.READ_REPOSITORY_FILE.equals(toolName)) {
                    addIfLimitExceeded(
                            exceeded,
                            "GitLab full file read budget exceeded",
                            gitLabReadFileCalls + 1,
                            properties.getMaxGitlabReadFileCalls()
                    );
                }
                if (isGitLabChunkTool(toolName)) {
                    addIfLimitExceeded(
                            exceeded,
                            "GitLab chunk read budget exceeded",
                            gitLabReadChunkCalls + 1,
                            properties.getMaxGitlabReadChunkCalls()
                    );
                }
                addIfLimitExceeded(
                        exceeded,
                        "GitLab returned character budget already exhausted",
                        gitLabReturnedCharacters,
                        properties.getMaxGitlabReturnedCharacters()
                );
            }
            case "database" -> {
                addIfLimitExceeded(exceeded, "Database tool call budget exceeded", dbCalls + 1, properties.getMaxDbCalls());
                if (isRawSqlTool(toolName)) {
                    addIfLimitExceeded(
                            exceeded,
                            "Database raw SQL tool call budget exceeded",
                            dbRawSqlCalls + 1,
                            properties.getMaxDbRawSqlCalls()
                    );
                }
                addIfLimitExceeded(
                        exceeded,
                        "Database returned character budget already exhausted",
                        dbReturnedCharacters,
                        properties.getMaxDbReturnedCharacters()
                );
            }
            case "operational-context" -> {
                addIfLimitExceeded(
                        exceeded,
                        "Operational context tool call budget exceeded",
                        operationalContextCalls + 1,
                        properties.getMaxOperationalContextCalls()
                );
                addIfLimitExceeded(
                        exceeded,
                        "Operational context returned character budget already exhausted",
                        operationalContextReturnedCharacters,
                        properties.getMaxOperationalContextReturnedCharacters()
                );
            }
            default -> {
            }
        }

        return exceeded;
    }

    private List<String> exceededAfterInvocation(String toolName) {
        var exceeded = new ArrayList<String>();
        if (toolGroup(toolName).equals("gitlab")) {
            addIfLimitExceeded(
                    exceeded,
                    "GitLab returned character budget exceeded",
                    gitLabReturnedCharacters,
                    properties.getMaxGitlabReturnedCharacters()
            );
        }
        if (toolGroup(toolName).equals("database")) {
            addIfLimitExceeded(
                    exceeded,
                    "Database returned character budget exceeded",
                    dbReturnedCharacters,
                    properties.getMaxDbReturnedCharacters()
            );
        }
        if (toolGroup(toolName).equals("operational-context")) {
            addIfLimitExceeded(
                    exceeded,
                    "Operational context returned character budget exceeded",
                    operationalContextReturnedCharacters,
                    properties.getMaxOperationalContextReturnedCharacters()
            );
        }
        return exceeded;
    }

    private void addIfLimitExceeded(List<String> exceeded, String label, long actual, long limit) {
        if (limit >= 0 && actual > limit) {
            exceeded.add("%s actual=%d limit=%d".formatted(label, actual, limit));
        }
    }

    private boolean isGitLabSearchTool(String toolName) {
        return GitLabToolNames.LIST_AVAILABLE_REPOSITORIES.equals(toolName)
                || GitLabToolNames.LIST_REPOSITORY_ENDPOINTS.equals(toolName)
                || GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES.equals(toolName)
                || GitLabToolNames.FIND_CLASS_REFERENCES.equals(toolName)
                || GitLabToolNames.FIND_FLOW_CONTEXT.equals(toolName);
    }

    private boolean isGitLabChunkTool(String toolName) {
        return GitLabToolNames.READ_REPOSITORY_FILE_CHUNK.equals(toolName)
                || GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(toolName);
    }

    private boolean isRawSqlTool(String toolName) {
        return DatabaseToolNames.EXECUTE_READONLY_SQL.equals(toolName);
    }

    private String toolGroup(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "unknown";
        }
        if (toolName.startsWith(ElasticToolNames.PREFIX)) {
            return "elasticsearch";
        }
        if (toolName.startsWith(GitLabToolNames.PREFIX)) {
            return "gitlab";
        }
        if (toolName.startsWith(DatabaseToolNames.PREFIX)) {
            return "database";
        }
        if (toolName.startsWith(OperationalContextToolNames.PREFIX)) {
            return "operational-context";
        }
        return "other";
    }

    public record CopilotToolBudgetSnapshot(
            String sessionId,
            int totalCalls,
            int elasticCalls,
            int gitLabCalls,
            int gitLabSearchCalls,
            int gitLabReadFileCalls,
            int gitLabReadChunkCalls,
            long gitLabReturnedCharacters,
            int dbCalls,
            int dbRawSqlCalls,
            long dbReturnedCharacters,
            int operationalContextCalls,
            long operationalContextReturnedCharacters,
            int softLimitExceededCount,
            int deniedToolCalls,
            int rawSqlAttempts,
            List<String> warnings
    ) {
        public CopilotToolBudgetSnapshot {
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }
    }
}
