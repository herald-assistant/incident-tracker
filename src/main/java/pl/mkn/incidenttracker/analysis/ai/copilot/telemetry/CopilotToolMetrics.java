package pl.mkn.incidenttracker.analysis.ai.copilot.telemetry;

import org.springframework.util.StringUtils;

public record CopilotToolMetrics(
        String analysisRunId,
        String copilotSessionId,
        String toolCallId,
        String toolName,
        String toolGroup,
        long latencyMs,
        long rawResultCharacters,
        boolean gitLabReadFileCall,
        boolean gitLabReadChunkCall,
        boolean databaseQueryCall,
        boolean databaseRawSqlCall
) {

    public static CopilotToolMetrics from(
            String analysisRunId,
            String copilotSessionId,
            String toolCallId,
            String toolName,
            long latencyMs,
            String rawResult
    ) {
        return new CopilotToolMetrics(
                analysisRunId,
                copilotSessionId,
                toolCallId,
                toolName,
                toolGroup(toolName),
                latencyMs,
                rawResult != null ? rawResult.length() : 0L,
                "gitlab_read_repository_file".equals(toolName),
                "gitlab_read_repository_file_chunk".equals(toolName)
                        || "gitlab_read_repository_file_chunks".equals(toolName),
                StringUtils.hasText(toolName) && toolName.startsWith("db_"),
                "db_execute_readonly_sql".equals(toolName)
        );
    }

    public static String toolGroup(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "unknown";
        }
        if (toolName.startsWith("elastic_")) {
            return "elasticsearch";
        }
        if (toolName.startsWith("gitlab_")) {
            return "gitlab";
        }
        if (toolName.startsWith("db_")) {
            return "database";
        }
        return "other";
    }
}
