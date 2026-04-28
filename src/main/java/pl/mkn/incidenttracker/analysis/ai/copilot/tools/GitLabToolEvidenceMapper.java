package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFindClassReferencesToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFindFlowContextToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileOutlineToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabSearchRepositoryCandidatesToolResponse;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitLabToolEvidenceMapper {

    private static final String TOOL_NAME_FILE = "gitlab_read_repository_file";
    private static final String TOOL_NAME_FILE_CHUNK = "gitlab_read_repository_file_chunk";
    private static final String TOOL_NAME_FILE_CHUNKS = "gitlab_read_repository_file_chunks";
    private static final String TOOL_NAME_FILE_OUTLINE = "gitlab_read_repository_file_outline";
    private static final String TOOL_NAME_SEARCH = "gitlab_search_repository_candidates";
    private static final String TOOL_NAME_CLASS_REFERENCES = "gitlab_find_class_references";
    private static final String TOOL_NAME_FLOW_CONTEXT = "gitlab_find_flow_context";

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
                 TOOL_NAME_FILE_OUTLINE,
                 TOOL_NAME_SEARCH,
                 TOOL_NAME_CLASS_REFERENCES,
                 TOOL_NAME_FLOW_CONTEXT -> true;
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
            case TOOL_NAME_FILE -> captureGitLabFile(rawArguments, rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNK -> captureGitLabFileChunk(rawArguments, rawResult, accumulator);
            case TOOL_NAME_FILE_CHUNKS -> captureGitLabFileChunks(rawArguments, rawResult, accumulator);
            case TOOL_NAME_FILE_OUTLINE -> captureGitLabFileOutline(toolCallId, rawArguments, rawResult, accumulator);
            case TOOL_NAME_SEARCH -> captureGitLabSearch(toolCallId, rawArguments, rawResult, accumulator);
            case TOOL_NAME_CLASS_REFERENCES -> captureGitLabClassReferences(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    accumulator
            );
            case TOOL_NAME_FLOW_CONTEXT -> captureGitLabFlowContext(toolCallId, rawArguments, rawResult, accumulator);
            default -> null;
        };
    }

    private AnalysisEvidenceSection captureGitLabFile(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileToolResponse.class);
            return accumulator.upsertGitLabItem(
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    buildGitLabFileItem(
                            response.projectName(),
                            response.filePath(),
                            toolReason(rawArguments),
                            response.content(),
                            null
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool file result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunk(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunkToolResponse.class);
            return accumulator.upsertGitLabItem(
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    buildGitLabFileItem(
                            response.projectName(),
                            response.filePath(),
                            toolReason(rawArguments),
                            response.content(),
                            response.returnedStartLine()
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool chunk result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunks(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunksToolResponse.class);
            var reason = toolReason(rawArguments);
            AnalysisEvidenceSection updatedSection = null;

            for (var chunk : safeList(response.chunks())) {
                if (chunk == null) {
                    continue;
                }

                updatedSection = accumulator.upsertGitLabItem(
                        gitLabFileKey(chunk.group(), chunk.projectName(), chunk.branch(), chunk.filePath()),
                        buildGitLabFileItem(
                                chunk.projectName(),
                                chunk.filePath(),
                                reason,
                                chunk.content(),
                                chunk.returnedStartLine()
                        )
                );
            }

            return updatedSection;
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool chunks result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileOutline(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileOutlineToolResponse.class);
            var attributes = buildGitLabDiscoveryAttributes(TOOL_NAME_FILE_OUTLINE, rawArguments);
            addAttribute(attributes, "projectName", response.projectName());
            addAttribute(attributes, "filePath", response.filePath());
            addAttribute(attributes, "packageName", response.packageName());
            addAttribute(attributes, "inferredRole", response.inferredRole());
            addAttribute(attributes, "truncated", String.valueOf(response.truncated()));
            addAttribute(attributes, "classCount", String.valueOf(safeList(response.classes()).size()));
            addAttribute(attributes, "methodSignatureCount", String.valueOf(safeList(response.methodSignatures()).size()));
            addJsonAttribute(attributes, "imports", response.imports());
            addJsonAttribute(attributes, "classes", response.classes());
            addJsonAttribute(attributes, "annotations", response.annotations());
            addJsonAttribute(attributes, "methodSignatures", response.methodSignatures());

            return accumulator.appendGitLabDiscoveryItem(
                    discoveryKey(toolCallId, TOOL_NAME_FILE_OUTLINE),
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(TOOL_NAME_FILE_OUTLINE),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool outline result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabSearch(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabSearchRepositoryCandidatesToolResponse.class);
            var candidates = safeList(response.candidates());
            var attributes = buildGitLabDiscoveryAttributes(TOOL_NAME_SEARCH, rawArguments);
            addAttribute(attributes, "candidateCount", String.valueOf(candidates.size()));
            addJsonAttribute(attributes, "candidates", candidates);

            return accumulator.appendGitLabDiscoveryItem(
                    discoveryKey(toolCallId, TOOL_NAME_SEARCH),
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(TOOL_NAME_SEARCH),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool search result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabClassReferences(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabFindClassReferencesToolResponse.class);
            var groups = safeList(response.groups());
            var recommendedNextReads = safeList(response.recommendedNextReads());
            var attributes = buildGitLabDiscoveryAttributes(TOOL_NAME_CLASS_REFERENCES, rawArguments);
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "searchedClass", response.searchedClass());
            addAttribute(attributes, "groupCount", String.valueOf(groups.size()));
            addAttribute(attributes, "candidateCount", String.valueOf(candidateCount(groups)));
            addAttribute(attributes, "recommendedNextReadCount", String.valueOf(recommendedNextReads.size()));
            addJsonAttribute(attributes, "searchKeywords", response.searchKeywords());
            addJsonAttribute(attributes, "groups", groups);
            addJsonAttribute(attributes, "recommendedNextReads", recommendedNextReads);

            return accumulator.appendGitLabDiscoveryItem(
                    discoveryKey(toolCallId, TOOL_NAME_CLASS_REFERENCES),
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(TOOL_NAME_CLASS_REFERENCES),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool class references result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFlowContext(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceCaptureRegistry.SessionArtifactAccumulator accumulator
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabFindFlowContextToolResponse.class);
            var groups = safeList(response.groups());
            var recommendedNextReads = safeList(response.recommendedNextReads());
            var attributes = buildGitLabDiscoveryAttributes(TOOL_NAME_FLOW_CONTEXT, rawArguments);
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "groupCount", String.valueOf(groups.size()));
            addAttribute(attributes, "candidateCount", String.valueOf(candidateCount(groups)));
            addAttribute(attributes, "recommendedNextReadCount", String.valueOf(recommendedNextReads.size()));
            addJsonAttribute(attributes, "groups", groups);
            addJsonAttribute(attributes, "recommendedNextReads", recommendedNextReads);

            return accumulator.appendGitLabDiscoveryItem(
                    discoveryKey(toolCallId, TOOL_NAME_FLOW_CONTEXT),
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(TOOL_NAME_FLOW_CONTEXT),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool flow context result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceItem buildGitLabFileItem(
            String projectName,
            String filePath,
            String reason,
            String content,
            Integer startLine
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "filePath", filePath);
        addAttribute(attributes, "reason", reason);
        addAttribute(attributes, "content", content);
        if (startLine != null && startLine > 0) {
            addAttribute(attributes, "startLine", String.valueOf(startLine));
        }

        return new AnalysisEvidenceItem(
                gitLabItemTitle(projectName, filePath),
                List.copyOf(attributes)
        );
    }

    private String toolReason(String rawArguments) {
        var argumentsNode = payloadReader.readJsonNode(rawArguments);
        return payloadReader.readTopLevelText(argumentsNode, "reason");
    }

    private List<AnalysisEvidenceAttribute> buildGitLabDiscoveryAttributes(String toolName, String rawArguments) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "toolName", toolName);
        addAttribute(attributes, "reason", toolReason(rawArguments));
        return attributes;
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private void addJsonAttribute(List<AnalysisEvidenceAttribute> attributes, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }

        try {
            addAttribute(
                    attributes,
                    name,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to render GitLab tool evidence attribute. name={} reason={}", name, exception.getMessage());
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private int candidateCount(List<GitLabFlowContextGroup> groups) {
        return safeList(groups).stream()
                .mapToInt(group -> group != null ? safeList(group.candidates()).size() : 0)
                .sum();
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
            return projectName.trim() + " · " + filePath.trim();
        }

        if (StringUtils.hasText(filePath)) {
            return filePath.trim();
        }

        return "GitLab file";
    }

    private String discoveryKey(String toolCallId, String toolName) {
        return StringUtils.hasText(toolCallId) ? toolCallId.trim() : toolName;
    }

    private String gitLabDiscoveryTitle(String toolName) {
        return switch (toolName) {
            case TOOL_NAME_SEARCH -> "GitLab search candidates";
            case TOOL_NAME_FILE_OUTLINE -> "GitLab file outline";
            case TOOL_NAME_CLASS_REFERENCES -> "GitLab class references";
            case TOOL_NAME_FLOW_CONTEXT -> "GitLab flow context";
            default -> "GitLab tool result";
        };
    }

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
