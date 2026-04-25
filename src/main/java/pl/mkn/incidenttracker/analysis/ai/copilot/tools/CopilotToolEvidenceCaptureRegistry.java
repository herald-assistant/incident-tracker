package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabFileChunkResult;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabReadRepositoryFileToolResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CopilotToolEvidenceCaptureRegistry {

    private static final String DATABASE_PROVIDER = "database";
    private static final String DATABASE_TOOL_CATEGORY = "tool-results";
    private static final String GITLAB_PROVIDER = "gitlab";
    private static final String GITLAB_TOOL_CATEGORY = "tool-fetched-code";
    private static final String TOOL_NAME_FILE = "gitlab_read_repository_file";
    private static final String TOOL_NAME_FILE_CHUNK = "gitlab_read_repository_file_chunk";
    private static final String TOOL_NAME_FILE_CHUNKS = "gitlab_read_repository_file_chunks";

    private final ObjectMapper objectMapper;
    private final Map<String, SessionArtifactAccumulator> sessionAccumulators = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, AnalysisAiToolEvidenceListener listener) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessionAccumulators.put(
                sessionId,
                new SessionArtifactAccumulator(
                        listener != null ? listener : AnalysisAiToolEvidenceListener.NO_OP
                )
        );
    }

    public void unregisterSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessionAccumulators.remove(sessionId);
    }

    public void captureToolResult(
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(toolName) || !StringUtils.hasText(rawResult)) {
            return;
        }

        var accumulator = sessionAccumulators.get(sessionId);
        if (accumulator == null) {
            return;
        }

        var updatedSection = switch (toolName) {
            case TOOL_NAME_FILE -> captureGitLabFile(rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNK -> captureGitLabFileChunk(rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNKS -> captureGitLabFileChunks(rawResult, accumulator);
            default -> isDatabaseTool(toolName)
                    ? captureDatabaseToolResult(toolCallId, toolName, rawArguments, rawResult, accumulator)
                    : null;
        };

        if (updatedSection == null || !updatedSection.hasItems()) {
            return;
        }

        try {
            accumulator.listener().onToolEvidenceUpdated(updatedSection);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to publish captured tool evidence sessionId={} toolName={} reason={}",
                    sessionId,
                    toolName,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private AnalysisEvidenceSection captureDatabaseToolResult(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        var resultNode = readJsonNode(rawResult);
        var argumentsNode = readJsonNode(rawArguments);
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "environment", readTopLevelText(resultNode, "environment"));
        addAttribute(attributes, "databaseAlias", readTopLevelText(resultNode, "databaseAlias"));
        addAttribute(attributes, "parameters", prettyPayload(argumentsNode, rawArguments, "{}"));
        addAttribute(attributes, "result", prettyPayload(resultNode, rawResult, ""));

        return accumulator.appendDatabaseItem(
                databaseToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem(
                        databaseToolTitle(toolName),
                        List.copyOf(attributes)
                )
        );
    }

    private AnalysisEvidenceSection captureGitLabFile(
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileToolResponse.class);
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "projectName", response.projectName());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "filePath", response.filePath());
            addAttribute(attributes, "referenceType", "AI_TOOL_FILE");
            addAttribute(attributes, "toolName", TOOL_NAME_FILE);
            addAttribute(attributes, "content", response.content());
            addAttribute(attributes, "contentTruncated", String.valueOf(response.truncated()));

            return accumulator.upsertGitLabItem(
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    new AnalysisEvidenceItem(
                            gitLabItemTitle(response.projectName(), response.filePath()),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool file result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunk(
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunkToolResponse.class);
            return accumulator.upsertGitLabItem(
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    buildGitLabChunkItem(
                            response.group(),
                            response.projectName(),
                            response.branch(),
                            response.filePath(),
                            TOOL_NAME_FILE_CHUNK,
                            response.requestedStartLine(),
                            response.requestedEndLine(),
                            response.returnedStartLine(),
                            response.returnedEndLine(),
                            response.totalLines(),
                            response.content(),
                            response.truncated()
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool chunk result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunks(
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunksToolResponse.class);
            AnalysisEvidenceSection updatedSection = null;

            for (var chunk : safeList(response.chunks())) {
                if (chunk == null) {
                    continue;
                }

                updatedSection = accumulator.upsertGitLabItem(
                        gitLabFileKey(chunk.group(), chunk.projectName(), chunk.branch(), chunk.filePath()),
                        buildGitLabChunkItem(chunk, TOOL_NAME_FILE_CHUNKS)
                );
            }

            return updatedSection;
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool chunks result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceItem buildGitLabChunkItem(GitLabFileChunkResult chunk, String toolName) {
        return buildGitLabChunkItem(
                chunk.group(),
                chunk.projectName(),
                chunk.branch(),
                chunk.filePath(),
                toolName,
                chunk.requestedStartLine(),
                chunk.requestedEndLine(),
                chunk.returnedStartLine(),
                chunk.returnedEndLine(),
                chunk.totalLines(),
                chunk.content(),
                chunk.truncated()
        );
    }

    private AnalysisEvidenceItem buildGitLabChunkItem(
            String group,
            String projectName,
            String branch,
            String filePath,
            String toolName,
            int requestedStartLine,
            int requestedEndLine,
            int returnedStartLine,
            int returnedEndLine,
            int totalLines,
            String content,
            boolean truncated
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "group", group);
        addAttribute(attributes, "projectName", projectName);
        addAttribute(attributes, "branch", branch);
        addAttribute(attributes, "filePath", filePath);
        addAttribute(attributes, "referenceType", "AI_TOOL_FILE_CHUNK");
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "requestedStartLine", String.valueOf(requestedStartLine));
        addAttribute(attributes, "requestedEndLine", String.valueOf(requestedEndLine));
        addAttribute(attributes, "returnedStartLine", String.valueOf(returnedStartLine));
        addAttribute(attributes, "returnedEndLine", String.valueOf(returnedEndLine));
        addAttribute(attributes, "totalLines", String.valueOf(totalLines));
        addAttribute(attributes, "content", content);
        addAttribute(attributes, "contentTruncated", String.valueOf(truncated));

        return new AnalysisEvidenceItem(
                gitLabItemTitle(projectName, filePath),
                List.copyOf(attributes)
        );
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private JsonNode readJsonNode(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }

        try {
            return objectMapper.readTree(rawPayload);
        }
        catch (JsonProcessingException exception) {
            log.warn("Failed to parse tool payload as JSON. reason={}", exception.getMessage());
            return null;
        }
    }

    private String prettyPayload(JsonNode payload, String rawPayload, String fallback) {
        if (payload == null || payload.isNull()) {
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            log.warn("Failed to pretty print tool payload. reason={}", exception.getMessage());
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }
    }

    private String readTopLevelText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }

        var field = payload.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }

        if (field.isValueNode()) {
            return field.asText();
        }

        return prettyPayload(field, field.toString(), null);
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private String gitLabFileKey(String group, String projectName, String branch, String filePath) {
        return String.join(
                "::",
                safeKeyPart(group),
                safeKeyPart(projectName),
                safeKeyPart(branch),
                safeKeyPart(filePath)
        );
    }

    private String gitLabItemTitle(String projectName, String filePath) {
        if (StringUtils.hasText(projectName) && StringUtils.hasText(filePath)) {
            return projectName.trim() + " file " + filePath.trim();
        }

        if (StringUtils.hasText(filePath)) {
            return filePath.trim();
        }

        return "GitLab tool fetched file";
    }

    private boolean isDatabaseTool(String toolName) {
        return StringUtils.hasText(toolName) && toolName.trim().startsWith("db_");
    }

    private String databaseToolKey(String toolCallId, String toolName) {
        if (StringUtils.hasText(toolCallId)) {
            return toolCallId.trim();
        }
        return "db-tool::" + safeKeyPart(toolName);
    }

    private String databaseToolTitle(String toolName) {
        return StringUtils.hasText(toolName) ? toolName.trim() : "database-tool";
    }

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private static boolean isChunkItem(AnalysisEvidenceItem item) {
        return item.attributes().stream()
                .anyMatch(attribute -> "requestedStartLine".equals(attribute.name()));
    }

    private record SessionArtifactAccumulator(
            AnalysisAiToolEvidenceListener listener,
            LinkedHashMap<String, AnalysisEvidenceItem> gitLabItems,
            LinkedHashMap<String, AnalysisEvidenceItem> databaseItems
    ) {
        private SessionArtifactAccumulator(AnalysisAiToolEvidenceListener listener) {
            this(listener, new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        private synchronized AnalysisEvidenceSection upsertGitLabItem(String key, AnalysisEvidenceItem candidate) {
            var current = gitLabItems.get(key);
            if (current != null && !isChunkItem(current) && isChunkItem(candidate)) {
                return new AnalysisEvidenceSection(
                        GITLAB_PROVIDER,
                        GITLAB_TOOL_CATEGORY,
                        List.copyOf(gitLabItems.values())
                );
            }

            gitLabItems.put(key, candidate);
            return new AnalysisEvidenceSection(
                    GITLAB_PROVIDER,
                    GITLAB_TOOL_CATEGORY,
                    List.copyOf(gitLabItems.values())
            );
        }

        private synchronized AnalysisEvidenceSection appendDatabaseItem(
                String key,
                AnalysisEvidenceItem candidate
        ) {
            var effectiveKey = key;
            if (!StringUtils.hasText(effectiveKey) || databaseItems.containsKey(effectiveKey)) {
                effectiveKey = (StringUtils.hasText(key) ? key : "db-tool")
                        + "::"
                        + (databaseItems.size() + 1);
            }

            databaseItems.put(effectiveKey, candidate);
            return new AnalysisEvidenceSection(
                    DATABASE_PROVIDER,
                    DATABASE_TOOL_CATEGORY,
                    List.copyOf(databaseItems.values())
            );
        }
    }

}
