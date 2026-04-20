package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabReadRepositoryFileChunkToolResponse;
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

    private static final String GITLAB_PROVIDER = "gitlab";
    private static final String GITLAB_TOOL_CATEGORY = "tool-fetched-code";
    private static final String TOOL_NAME_FILE = "gitlab_read_repository_file";
    private static final String TOOL_NAME_FILE_CHUNK = "gitlab_read_repository_file_chunk";

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

    public void captureToolResult(String sessionId, String toolName, String rawResult) {
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
            default -> null;
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
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "projectName", response.projectName());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "filePath", response.filePath());
            addAttribute(attributes, "referenceType", "AI_TOOL_FILE_CHUNK");
            addAttribute(attributes, "toolName", TOOL_NAME_FILE_CHUNK);
            addAttribute(attributes, "requestedStartLine", String.valueOf(response.requestedStartLine()));
            addAttribute(attributes, "requestedEndLine", String.valueOf(response.requestedEndLine()));
            addAttribute(attributes, "returnedStartLine", String.valueOf(response.returnedStartLine()));
            addAttribute(attributes, "returnedEndLine", String.valueOf(response.returnedEndLine()));
            addAttribute(attributes, "totalLines", String.valueOf(response.totalLines()));
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
            log.warn("Failed to parse GitLab tool chunk result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
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

    private static boolean isChunkItem(AnalysisEvidenceItem item) {
        return item.attributes().stream()
                .anyMatch(attribute -> "requestedStartLine".equals(attribute.name()));
    }

    private record SessionArtifactAccumulator(
            AnalysisAiToolEvidenceListener listener,
            LinkedHashMap<String, AnalysisEvidenceItem> gitLabItems
    ) {
        private SessionArtifactAccumulator(AnalysisAiToolEvidenceListener listener) {
            this(listener, new LinkedHashMap<>());
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
    }

}
