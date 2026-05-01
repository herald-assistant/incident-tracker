package pl.mkn.incidenttracker.aiplatform.copilot.tools.telemetry;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames;
import pl.mkn.incidenttracker.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames;

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
                GitLabToolNames.READ_REPOSITORY_FILE.equals(toolName),
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK.equals(toolName)
                        || GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(toolName),
                StringUtils.hasText(toolName) && toolName.startsWith(DatabaseToolNames.PREFIX),
                DatabaseToolNames.EXECUTE_READONLY_SQL.equals(toolName)
        );
    }

    public static String toolGroup(String toolName) {
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
        return "other";
    }
}
