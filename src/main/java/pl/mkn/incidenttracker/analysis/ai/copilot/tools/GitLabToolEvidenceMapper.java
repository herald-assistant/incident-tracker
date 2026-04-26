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

    private final ObjectMapper objectMapper;
    private final ToolJsonPayloadReader payloadReader;

    public boolean supports(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }

        return switch (toolName) {
            case TOOL_NAME_FILE, TOOL_NAME_FILE_CHUNK, TOOL_NAME_FILE_CHUNKS -> true;
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

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
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
            return projectName.trim() + " · " + filePath.trim();
        }

        if (StringUtils.hasText(filePath)) {
            return filePath.trim();
        }

        return "GitLab file";
    }

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
