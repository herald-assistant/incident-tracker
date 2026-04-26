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
    private static final String GITLAB_TOOL_SEARCH_CATEGORY = "tool-search-results";
    private static final String GITLAB_TOOL_FLOW_CATEGORY = "tool-flow-context";
    private static final int MAX_SUMMARY_VALUES = 8;
    private static final String TOOL_NAME_FILE = "gitlab_read_repository_file";
    private static final String TOOL_NAME_FILE_CHUNK = "gitlab_read_repository_file_chunk";
    private static final String TOOL_NAME_FILE_CHUNKS = "gitlab_read_repository_file_chunks";
    private static final String TOOL_NAME_SEARCH = "gitlab_search_repository_candidates";
    private static final String TOOL_NAME_OUTLINE = "gitlab_read_repository_file_outline";
    private static final String TOOL_NAME_FLOW_CONTEXT = "gitlab_find_flow_context";
    private static final String TOOL_NAME_CLASS_REFERENCES = "gitlab_find_class_references";

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
            case TOOL_NAME_SEARCH -> captureGitLabSearch(toolCallId, toolName, rawArguments, rawResult, accumulator);
            case TOOL_NAME_OUTLINE -> captureGitLabOutline(toolCallId, toolName, rawArguments, rawResult, accumulator);
            case TOOL_NAME_FLOW_CONTEXT, TOOL_NAME_CLASS_REFERENCES ->
                    captureGitLabFlowContext(toolCallId, toolName, rawArguments, rawResult, accumulator);
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
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "diagnosticQuestion", databaseDiagnosticQuestion(toolName, argumentsNode));
        addAttribute(attributes, "environment", readTopLevelText(resultNode, "environment"));
        addAttribute(attributes, "databaseAlias", readTopLevelText(resultNode, "databaseAlias"));
        addAttribute(attributes, "parameters", prettyPayload(argumentsNode, rawArguments, "{}"));
        addAttribute(attributes, "resultSummary", databaseResultSummary(resultNode));
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

    private AnalysisEvidenceSection captureGitLabSearch(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        var resultNode = readJsonNode(rawResult);
        var argumentsNode = readJsonNode(rawArguments);
        var candidates = field(resultNode, "candidates");
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "seed", gitLabSeed(argumentsNode));
        addAttribute(attributes, "projectCandidates", summarizeValues(candidateFieldValues(candidates, "projectName")));
        addAttribute(attributes, "fileCandidates", summarizeValues(candidateFileValues(candidates)));
        addAttribute(attributes, "selectedReason", firstCandidateField(candidates, "matchReason"));
        addAttribute(attributes, "resultSummary", "candidateCount=" + arraySize(candidates));

        return accumulator.appendGitLabSearchItem(
                gitLabToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem("GitLab search results", List.copyOf(attributes))
        );
    }

    private AnalysisEvidenceSection captureGitLabOutline(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        var resultNode = readJsonNode(rawResult);
        var argumentsNode = readJsonNode(rawArguments);
        var projectName = readTopLevelText(resultNode, "projectName");
        var filePath = readTopLevelText(resultNode, "filePath");
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "seed", gitLabSeed(argumentsNode));
        addAttribute(attributes, "projectCandidates", projectName);
        addAttribute(attributes, "fileCandidates", gitLabFileValue(projectName, filePath));
        addAttribute(attributes, "selectedReason", outlineSelectedReason(resultNode));
        addAttribute(attributes, "resultSummary", outlineResultSummary(resultNode));

        return accumulator.appendGitLabFlowItem(
                gitLabToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem(
                        StringUtils.hasText(filePath)
                                ? "GitLab file outline " + filePath
                                : "GitLab file outline",
                        List.copyOf(attributes)
                )
        );
    }

    private AnalysisEvidenceSection captureGitLabFlowContext(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            SessionArtifactAccumulator accumulator
    ) {
        var resultNode = readJsonNode(rawResult);
        var argumentsNode = readJsonNode(rawArguments);
        var groups = field(resultNode, "groups");
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "seed", gitLabSeed(argumentsNode));
        addAttribute(attributes, "projectCandidates", summarizeValues(flowCandidateFieldValues(groups, "projectName")));
        addAttribute(attributes, "fileCandidates", summarizeValues(flowCandidateFileValues(groups)));
        addAttribute(attributes, "selectedReason", flowSelectedReason(groups, field(resultNode, "recommendedNextReads")));
        addAttribute(attributes, "resultSummary", flowResultSummary(groups, field(resultNode, "recommendedNextReads")));

        return accumulator.appendGitLabFlowItem(
                gitLabToolKey(toolCallId, toolName),
                new AnalysisEvidenceItem(gitLabFlowTitle(toolName), List.copyOf(attributes))
        );
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

    private String databaseDiagnosticQuestion(String toolName, JsonNode argumentsNode) {
        var reason = readTopLevelText(argumentsNode, "reason");
        if (StringUtils.hasText(reason)) {
            return reason;
        }

        var sql = readTopLevelText(argumentsNode, "sql");
        if (StringUtils.hasText(sql)) {
            return "Validate data hypothesis with readonly SQL: " + abbreviate(sql, 160);
        }

        var table = tableRef(field(argumentsNode, "table"));
        if (StringUtils.hasText(table)) {
            return "Inspect " + table + " with " + safeKeyPart(toolName);
        }

        var tables = tableRefs(field(argumentsNode, "tables"));
        if (StringUtils.hasText(tables)) {
            return "Inspect joined tables " + tables + " with " + safeKeyPart(toolName);
        }

        var discoverySeed = joinNonBlank(
                "application=" + readTopLevelText(argumentsNode, "applicationNamePattern"),
                "table=" + readTopLevelText(argumentsNode, "tableNamePattern"),
                "column=" + readTopLevelText(argumentsNode, "columnNamePattern"),
                "hint=" + readTopLevelText(argumentsNode, "entityOrKeywordHint")
        );
        if (StringUtils.hasText(discoverySeed)) {
            return "Find database objects for " + discoverySeed;
        }

        return "Run " + safeKeyPart(toolName) + " for current incident database scope.";
    }

    private String databaseResultSummary(JsonNode resultNode) {
        if (resultNode == null || !resultNode.isObject()) {
            return null;
        }

        var parts = new ArrayList<String>();
        addSummaryPart(parts, "table", tableRef(field(resultNode, "table")));
        addSummaryPart(parts, "count", readTopLevelText(resultNode, "count"));
        addSummaryPart(parts, "exists", readTopLevelText(resultNode, "exists"));
        addSummaryPart(parts, "candidateCount", arraySizeText(field(resultNode, "candidates")));
        addSummaryPart(parts, "rowCount", arraySizeText(field(resultNode, "rows")));
        addSummaryPart(parts, "groupCount", arraySizeText(field(resultNode, "groups")));
        addSummaryPart(parts, "relationshipCount", arraySizeText(field(resultNode, "relationships")));
        addSummaryPart(parts, "columnCount", arraySizeText(field(resultNode, "columns")));
        addSummaryPart(parts, "truncated", readTopLevelText(resultNode, "truncated"));
        addSummaryPart(parts, "warningCount", arraySizeText(field(resultNode, "warnings")));

        if (parts.isEmpty()) {
            return "resultFields=" + summarizeValues(fieldNames(resultNode));
        }

        return String.join(" ", parts);
    }

    private String gitLabSeed(JsonNode argumentsNode) {
        if (argumentsNode == null || !argumentsNode.isObject()) {
            return null;
        }

        var values = new ArrayList<String>();
        addSeedPart(values, "projects", summarizeJsonArray(field(argumentsNode, "projectNames")));
        addSeedPart(values, "operations", summarizeJsonArray(field(argumentsNode, "operationNames")));
        addSeedPart(values, "keywords", summarizeJsonArray(field(argumentsNode, "keywords")));
        addSeedPart(values, "class", readTopLevelText(argumentsNode, "className"));
        addSeedPart(values, "seedClass", readTopLevelText(argumentsNode, "seedClass"));
        addSeedPart(values, "seedMethod", readTopLevelText(argumentsNode, "seedMethod"));
        addSeedPart(values, "seedFile", readTopLevelText(argumentsNode, "seedFilePath"));
        addSeedPart(values, "file", readTopLevelText(argumentsNode, "filePath"));

        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String outlineSelectedReason(JsonNode resultNode) {
        var values = new ArrayList<String>();
        addSeedPart(values, "inferredRole", readTopLevelText(resultNode, "inferredRole"));
        addSeedPart(values, "package", readTopLevelText(resultNode, "packageName"));
        addSeedPart(values, "classes", summarizeJsonArray(field(resultNode, "classes")));
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String outlineResultSummary(JsonNode resultNode) {
        if (resultNode == null || !resultNode.isObject()) {
            return null;
        }

        return joinNonBlank(
                "package=" + readTopLevelText(resultNode, "packageName"),
                "classCount=" + arraySizeText(field(resultNode, "classes")),
                "methodSignatureCount=" + arraySizeText(field(resultNode, "methodSignatures")),
                "truncated=" + readTopLevelText(resultNode, "truncated")
        );
    }

    private String flowSelectedReason(JsonNode groups, JsonNode recommendedNextReads) {
        var roleSummary = flowRoleSummary(groups);
        var firstRead = firstArrayValue(recommendedNextReads);

        return joinNonBlank(
                StringUtils.hasText(roleSummary) ? "roles=" + roleSummary : null,
                StringUtils.hasText(firstRead) ? "firstRecommendedRead=" + firstRead : null
        );
    }

    private String flowResultSummary(JsonNode groups, JsonNode recommendedNextReads) {
        return joinNonBlank(
                "groupCount=" + arraySizeText(groups),
                "candidateCount=" + flowCandidateCount(groups),
                "recommendedNextReadsCount=" + arraySizeText(recommendedNextReads)
        );
    }

    private List<String> candidateFieldValues(JsonNode candidates, String fieldName) {
        var values = new ArrayList<String>();
        for (var candidate : iterableArray(candidates)) {
            addDistinct(values, readTopLevelText(candidate, fieldName));
        }
        return values;
    }

    private List<String> candidateFileValues(JsonNode candidates) {
        var values = new ArrayList<String>();
        for (var candidate : iterableArray(candidates)) {
            addDistinct(values, gitLabFileValue(
                    readTopLevelText(candidate, "projectName"),
                    readTopLevelText(candidate, "filePath")
            ));
        }
        return values;
    }

    private List<String> flowCandidateFieldValues(JsonNode groups, String fieldName) {
        var values = new ArrayList<String>();
        for (var group : iterableArray(groups)) {
            for (var candidate : iterableArray(field(group, "candidates"))) {
                addDistinct(values, readTopLevelText(candidate, fieldName));
            }
        }
        return values;
    }

    private List<String> flowCandidateFileValues(JsonNode groups) {
        var values = new ArrayList<String>();
        for (var group : iterableArray(groups)) {
            for (var candidate : iterableArray(field(group, "candidates"))) {
                addDistinct(values, gitLabFileValue(
                        readTopLevelText(candidate, "projectName"),
                        readTopLevelText(candidate, "filePath")
                ));
            }
        }
        return values;
    }

    private int flowCandidateCount(JsonNode groups) {
        var count = 0;
        for (var group : iterableArray(groups)) {
            count += arraySize(field(group, "candidates"));
        }
        return count;
    }

    private String flowRoleSummary(JsonNode groups) {
        var values = new ArrayList<String>();
        for (var group : iterableArray(groups)) {
            var role = readTopLevelText(group, "role");
            if (StringUtils.hasText(role)) {
                values.add(role + "=" + arraySize(field(group, "candidates")));
            }
        }
        return summarizeValues(values);
    }

    private String firstCandidateField(JsonNode candidates, String fieldName) {
        for (var candidate : iterableArray(candidates)) {
            var value = readTopLevelText(candidate, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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

    private JsonNode field(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get(fieldName);
    }

    private List<JsonNode> iterableArray(JsonNode payload) {
        if (payload == null || !payload.isArray()) {
            return List.of();
        }

        var values = new ArrayList<JsonNode>();
        payload.forEach(values::add);
        return values;
    }

    private String arraySizeText(JsonNode payload) {
        return payload != null && payload.isArray() ? String.valueOf(payload.size()) : null;
    }

    private int arraySize(JsonNode payload) {
        return payload != null && payload.isArray() ? payload.size() : 0;
    }

    private List<String> fieldNames(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return List.of();
        }

        var values = new ArrayList<String>();
        var fieldNames = payload.fieldNames();
        while (fieldNames.hasNext()) {
            values.add(fieldNames.next());
        }
        return values;
    }

    private String firstArrayValue(JsonNode payload) {
        for (var item : iterableArray(payload)) {
            var value = nodeText(item);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String summarizeJsonArray(JsonNode payload) {
        var values = new ArrayList<String>();
        for (var item : iterableArray(payload)) {
            addDistinct(values, nodeText(item));
        }
        return summarizeValues(values);
    }

    private String summarizeValues(List<String> values) {
        var safeValues = values != null ? values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList() : List.<String>of();
        if (safeValues.isEmpty()) {
            return null;
        }

        var limit = Math.min(safeValues.size(), MAX_SUMMARY_VALUES);
        var preview = new ArrayList<>(safeValues.subList(0, limit));
        if (safeValues.size() > limit) {
            preview.add("... (" + safeValues.size() + " items)");
        }
        return String.join(", ", preview);
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private void addSummaryPart(List<String> parts, String name, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(name + "=" + value.trim());
        }
    }

    private void addSeedPart(List<String> parts, String name, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(name + "=" + value.trim());
        }
    }

    private void addDistinct(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    private String joinNonBlank(String... values) {
        var parts = new ArrayList<String>();
        for (var value : values) {
            if (StringUtils.hasText(value) && !value.endsWith("=null")) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String tableRefs(JsonNode tablesNode) {
        var values = new ArrayList<String>();
        for (var tableNode : iterableArray(tablesNode)) {
            addDistinct(values, tableRef(tableNode));
        }
        return summarizeValues(values);
    }

    private String tableRef(JsonNode tableNode) {
        if (tableNode == null || !tableNode.isObject()) {
            return null;
        }

        var schema = readTopLevelText(tableNode, "schema");
        var tableName = readTopLevelText(tableNode, "tableName");
        if (StringUtils.hasText(schema) && StringUtils.hasText(tableName)) {
            return schema.trim() + "." + tableName.trim();
        }
        if (StringUtils.hasText(tableName)) {
            return tableName.trim();
        }
        return readTopLevelText(tableNode, "name");
    }

    private String gitLabFileValue(String projectName, String filePath) {
        if (StringUtils.hasText(projectName) && StringUtils.hasText(filePath)) {
            return projectName.trim() + ":" + filePath.trim();
        }
        if (StringUtils.hasText(filePath)) {
            return filePath.trim();
        }
        return null;
    }

    private String gitLabToolKey(String toolCallId, String toolName) {
        if (StringUtils.hasText(toolCallId)) {
            return toolCallId.trim();
        }
        return "gitlab-tool::" + safeKeyPart(toolName);
    }

    private String gitLabFlowTitle(String toolName) {
        if (TOOL_NAME_CLASS_REFERENCES.equals(toolName)) {
            return "GitLab class reference context";
        }
        return "GitLab flow context";
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

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxLength
                ? normalized.substring(0, maxLength) + "...(" + normalized.length() + " chars)"
                : normalized;
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
            LinkedHashMap<String, AnalysisEvidenceItem> gitLabSearchItems,
            LinkedHashMap<String, AnalysisEvidenceItem> gitLabFlowItems,
            LinkedHashMap<String, AnalysisEvidenceItem> databaseItems
    ) {
        private SessionArtifactAccumulator(AnalysisAiToolEvidenceListener listener) {
            this(
                    listener,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>()
            );
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

        private synchronized AnalysisEvidenceSection appendGitLabSearchItem(
                String key,
                AnalysisEvidenceItem candidate
        ) {
            appendItem(gitLabSearchItems, key, "gitlab-search-tool", candidate);
            return new AnalysisEvidenceSection(
                    GITLAB_PROVIDER,
                    GITLAB_TOOL_SEARCH_CATEGORY,
                    List.copyOf(gitLabSearchItems.values())
            );
        }

        private synchronized AnalysisEvidenceSection appendGitLabFlowItem(
                String key,
                AnalysisEvidenceItem candidate
        ) {
            appendItem(gitLabFlowItems, key, "gitlab-flow-tool", candidate);
            return new AnalysisEvidenceSection(
                    GITLAB_PROVIDER,
                    GITLAB_TOOL_FLOW_CATEGORY,
                    List.copyOf(gitLabFlowItems.values())
            );
        }

        private synchronized AnalysisEvidenceSection appendDatabaseItem(
                String key,
                AnalysisEvidenceItem candidate
        ) {
            appendItem(databaseItems, key, "db-tool", candidate);
            return new AnalysisEvidenceSection(
                    DATABASE_PROVIDER,
                    DATABASE_TOOL_CATEGORY,
                    List.copyOf(databaseItems.values())
            );
        }

        private void appendItem(
                LinkedHashMap<String, AnalysisEvidenceItem> items,
                String key,
                String fallbackKey,
                AnalysisEvidenceItem candidate
        ) {
            var effectiveKey = key;
            if (!StringUtils.hasText(effectiveKey) || items.containsKey(effectiveKey)) {
                effectiveKey = (StringUtils.hasText(key) ? key : fallbackKey)
                        + "::"
                        + (items.size() + 1);
            }

            items.put(effectiveKey, candidate);
        }
    }

}
