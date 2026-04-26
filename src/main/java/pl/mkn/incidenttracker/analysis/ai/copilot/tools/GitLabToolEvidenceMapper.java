package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFileChunkResult;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitLabToolEvidenceMapper {

    private static final String TOOL_NAME_FILE = "gitlab_read_repository_file";
    private static final String TOOL_NAME_FILE_CHUNK = "gitlab_read_repository_file_chunk";
    private static final String TOOL_NAME_FILE_CHUNKS = "gitlab_read_repository_file_chunks";
    private static final String TOOL_NAME_SEARCH = "gitlab_search_repository_candidates";
    private static final String TOOL_NAME_OUTLINE = "gitlab_read_repository_file_outline";
    private static final String TOOL_NAME_FLOW_CONTEXT = "gitlab_find_flow_context";
    private static final String TOOL_NAME_CLASS_REFERENCES = "gitlab_find_class_references";

    private final ObjectMapper objectMapper;
    private final ToolJsonPayloadReader payloadReader;

    public boolean supports(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }

        return switch (toolName) {
            case TOOL_NAME_FILE,
                 TOOL_NAME_FILE_CHUNK,
                 TOOL_NAME_FILE_CHUNKS,
                 TOOL_NAME_SEARCH,
                 TOOL_NAME_OUTLINE,
                 TOOL_NAME_FLOW_CONTEXT,
                 TOOL_NAME_CLASS_REFERENCES -> true;
            default -> false;
        };
    }

    AnalysisEvidenceSection capture(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        return switch (toolName) {
            case TOOL_NAME_FILE -> captureGitLabFile(rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNK -> captureGitLabFileChunk(rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNKS -> captureGitLabFileChunks(rawResult, accumulator);
            case TOOL_NAME_SEARCH -> captureGitLabSearch(toolCallId, toolName, rawArguments, rawResult, accumulator);
            case TOOL_NAME_OUTLINE -> captureGitLabOutline(toolCallId, toolName, rawArguments, rawResult, accumulator);
            case TOOL_NAME_FLOW_CONTEXT, TOOL_NAME_CLASS_REFERENCES ->
                    captureGitLabFlowContext(toolCallId, toolName, rawArguments, rawResult, accumulator);
            default -> null;
        };
    }

    private AnalysisEvidenceSection captureGitLabFile(
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
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
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
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
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
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
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        var resultNode = payloadReader.readJsonNode(rawResult);
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var candidates = payloadReader.field(resultNode, "candidates");
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "seed", gitLabSeed(argumentsNode));
        addAttribute(attributes, "projectCandidates", payloadReader.summarizeValues(candidateFieldValues(candidates, "projectName")));
        addAttribute(attributes, "fileCandidates", payloadReader.summarizeValues(candidateFileValues(candidates)));
        addAttribute(attributes, "selectedReason", firstCandidateField(candidates, "matchReason"));
        addAttribute(attributes, "resultSummary", "candidateCount=" + payloadReader.arraySize(candidates));

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
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        var resultNode = payloadReader.readJsonNode(rawResult);
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var projectName = payloadReader.readTopLevelText(resultNode, "projectName");
        var filePath = payloadReader.readTopLevelText(resultNode, "filePath");
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
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        var resultNode = payloadReader.readJsonNode(rawResult);
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        var groups = payloadReader.field(resultNode, "groups");
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "seed", gitLabSeed(argumentsNode));
        addAttribute(attributes, "projectCandidates", payloadReader.summarizeValues(flowCandidateFieldValues(groups, "projectName")));
        addAttribute(attributes, "fileCandidates", payloadReader.summarizeValues(flowCandidateFileValues(groups)));
        addAttribute(attributes, "selectedReason", flowSelectedReason(groups, payloadReader.field(resultNode, "recommendedNextReads")));
        addAttribute(attributes, "resultSummary", flowResultSummary(groups, payloadReader.field(resultNode, "recommendedNextReads")));

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

    private String gitLabSeed(JsonNode argumentsNode) {
        if (argumentsNode == null || !argumentsNode.isObject()) {
            return null;
        }

        var values = new ArrayList<String>();
        addSeedPart(values, "projects", payloadReader.summarizeJsonArray(payloadReader.field(argumentsNode, "projectNames")));
        addSeedPart(values, "operations", payloadReader.summarizeJsonArray(payloadReader.field(argumentsNode, "operationNames")));
        addSeedPart(values, "keywords", payloadReader.summarizeJsonArray(payloadReader.field(argumentsNode, "keywords")));
        addSeedPart(values, "class", payloadReader.readTopLevelText(argumentsNode, "className"));
        addSeedPart(values, "seedClass", payloadReader.readTopLevelText(argumentsNode, "seedClass"));
        addSeedPart(values, "seedMethod", payloadReader.readTopLevelText(argumentsNode, "seedMethod"));
        addSeedPart(values, "seedFile", payloadReader.readTopLevelText(argumentsNode, "seedFilePath"));
        addSeedPart(values, "file", payloadReader.readTopLevelText(argumentsNode, "filePath"));

        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String outlineSelectedReason(JsonNode resultNode) {
        var values = new ArrayList<String>();
        addSeedPart(values, "inferredRole", payloadReader.readTopLevelText(resultNode, "inferredRole"));
        addSeedPart(values, "package", payloadReader.readTopLevelText(resultNode, "packageName"));
        addSeedPart(values, "classes", payloadReader.summarizeJsonArray(payloadReader.field(resultNode, "classes")));
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String outlineResultSummary(JsonNode resultNode) {
        if (resultNode == null || !resultNode.isObject()) {
            return null;
        }

        return payloadReader.joinNonBlank(
                "package=" + payloadReader.readTopLevelText(resultNode, "packageName"),
                "classCount=" + payloadReader.arraySizeText(payloadReader.field(resultNode, "classes")),
                "methodSignatureCount=" + payloadReader.arraySizeText(payloadReader.field(resultNode, "methodSignatures")),
                "truncated=" + payloadReader.readTopLevelText(resultNode, "truncated")
        );
    }

    private String flowSelectedReason(JsonNode groups, JsonNode recommendedNextReads) {
        var roleSummary = flowRoleSummary(groups);
        var firstRead = payloadReader.firstArrayValue(recommendedNextReads);

        return payloadReader.joinNonBlank(
                StringUtils.hasText(roleSummary) ? "roles=" + roleSummary : null,
                StringUtils.hasText(firstRead) ? "firstRecommendedRead=" + firstRead : null
        );
    }

    private String flowResultSummary(JsonNode groups, JsonNode recommendedNextReads) {
        return payloadReader.joinNonBlank(
                "groupCount=" + payloadReader.arraySizeText(groups),
                "candidateCount=" + flowCandidateCount(groups),
                "recommendedNextReadsCount=" + payloadReader.arraySizeText(recommendedNextReads)
        );
    }

    private List<String> candidateFieldValues(JsonNode candidates, String fieldName) {
        var values = new ArrayList<String>();
        for (var candidate : payloadReader.iterableArray(candidates)) {
            addDistinct(values, payloadReader.readTopLevelText(candidate, fieldName));
        }
        return values;
    }

    private List<String> candidateFileValues(JsonNode candidates) {
        var values = new ArrayList<String>();
        for (var candidate : payloadReader.iterableArray(candidates)) {
            addDistinct(values, gitLabFileValue(
                    payloadReader.readTopLevelText(candidate, "projectName"),
                    payloadReader.readTopLevelText(candidate, "filePath")
            ));
        }
        return values;
    }

    private List<String> flowCandidateFieldValues(JsonNode groups, String fieldName) {
        var values = new ArrayList<String>();
        for (var group : payloadReader.iterableArray(groups)) {
            for (var candidate : payloadReader.iterableArray(payloadReader.field(group, "candidates"))) {
                addDistinct(values, payloadReader.readTopLevelText(candidate, fieldName));
            }
        }
        return values;
    }

    private List<String> flowCandidateFileValues(JsonNode groups) {
        var values = new ArrayList<String>();
        for (var group : payloadReader.iterableArray(groups)) {
            for (var candidate : payloadReader.iterableArray(payloadReader.field(group, "candidates"))) {
                addDistinct(values, gitLabFileValue(
                        payloadReader.readTopLevelText(candidate, "projectName"),
                        payloadReader.readTopLevelText(candidate, "filePath")
                ));
            }
        }
        return values;
    }

    private int flowCandidateCount(JsonNode groups) {
        var count = 0;
        for (var group : payloadReader.iterableArray(groups)) {
            count += payloadReader.arraySize(payloadReader.field(group, "candidates"));
        }
        return count;
    }

    private String flowRoleSummary(JsonNode groups) {
        var values = new ArrayList<String>();
        for (var group : payloadReader.iterableArray(groups)) {
            var role = payloadReader.readTopLevelText(group, "role");
            if (StringUtils.hasText(role)) {
                values.add(role + "=" + payloadReader.arraySize(payloadReader.field(group, "candidates")));
            }
        }
        return payloadReader.summarizeValues(values);
    }

    private String firstCandidateField(JsonNode candidates, String fieldName) {
        for (var candidate : payloadReader.iterableArray(candidates)) {
            var value = payloadReader.readTopLevelText(candidate, fieldName);
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

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
