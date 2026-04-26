package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotToolMetrics;

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

    public CopilotToolBudgetProperties properties() {
        return properties;
    }

    public synchronized CopilotToolBudgetDecision beforeInvocation(String toolName) {
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
        if (!properties.active()) {
            return CopilotToolBudgetDecision.allowed(sessionId, toolName);
        }

        var rawResultCharacters = rawResult != null ? rawResult.length() : 0L;
        var toolMetrics = CopilotToolMetrics.from(null, sessionId, null, toolName, 0L, rawResult);
        totalCalls++;

        switch (toolMetrics.toolGroup()) {
            case "elasticsearch" -> elasticCalls++;
            case "gitlab" -> {
                gitLabCalls++;
                gitLabReturnedCharacters += rawResultCharacters;
            }
            case "database" -> {
                dbCalls++;
                dbReturnedCharacters += rawResultCharacters;
            }
            default -> {
            }
        }

        if (isGitLabSearchTool(toolName)) {
            gitLabSearchCalls++;
        }
        if (toolMetrics.gitLabReadFileCall()) {
            gitLabReadFileCalls++;
        }
        if (toolMetrics.gitLabReadChunkCall()) {
            gitLabReadChunkCalls++;
        }
        if (toolMetrics.databaseRawSqlCall()) {
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
                softLimitExceededCount,
                deniedToolCalls,
                rawSqlAttempts,
                warnings
        );
    }

    private List<String> exceededBeforeInvocation(String toolName) {
        var exceeded = new ArrayList<String>();
        addIfLimitExceeded(exceeded, "total tool call budget exceeded", totalCalls + 1, properties.getMaxTotalCalls());

        var toolGroup = CopilotToolMetrics.toolGroup(toolName);
        switch (toolGroup) {
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
                if ("gitlab_read_repository_file".equals(toolName)) {
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
            default -> {
            }
        }

        return exceeded;
    }

    private List<String> exceededAfterInvocation(String toolName) {
        var exceeded = new ArrayList<String>();
        if (CopilotToolMetrics.toolGroup(toolName).equals("gitlab")) {
            addIfLimitExceeded(
                    exceeded,
                    "GitLab returned character budget exceeded",
                    gitLabReturnedCharacters,
                    properties.getMaxGitlabReturnedCharacters()
            );
        }
        if (CopilotToolMetrics.toolGroup(toolName).equals("database")) {
            addIfLimitExceeded(
                    exceeded,
                    "Database returned character budget exceeded",
                    dbReturnedCharacters,
                    properties.getMaxDbReturnedCharacters()
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
        return "gitlab_search_repository_candidates".equals(toolName)
                || "gitlab_find_class_references".equals(toolName)
                || "gitlab_find_flow_context".equals(toolName);
    }

    private boolean isGitLabChunkTool(String toolName) {
        return "gitlab_read_repository_file_chunk".equals(toolName)
                || "gitlab_read_repository_file_chunks".equals(toolName);
    }

    private boolean isRawSqlTool(String toolName) {
        return "db_execute_readonly_sql".equals(toolName);
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
