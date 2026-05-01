package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.gitlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindClassReferencesToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindFlowContextToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileOutlineToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabSearchRepositoryCandidatesToolResponse;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.List;

import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.FIND_CLASS_REFERENCES;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.FIND_FLOW_CONTEXT;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNK;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitLabToolEvidenceMapper {

    private static final String GITLAB_PROVIDER = "gitlab";
    private static final String GITLAB_FETCHED_CODE_CATEGORY = "tool-fetched-code";
    private static final String GITLAB_DISCOVERY_CATEGORY = "tool-discovery";
    private static final String GITLAB_FILE_ORDER_NAMESPACE = "gitlab";
    private static final String GITLAB_FILE_FALLBACK_KEY = "gitlab-file";
    private static final String GITLAB_DISCOVERY_ORDER_NAMESPACE = "gitlab-discovery";
    private static final String GITLAB_DISCOVERY_FALLBACK_KEY = "gitlab-tool";

    private final ObjectMapper objectMapper;
    private final JsonPayloadReader payloadReader;

    public boolean supports(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }

        return switch (toolName) {
            case READ_REPOSITORY_FILE,
                 READ_REPOSITORY_FILE_CHUNK,
                 READ_REPOSITORY_FILE_CHUNKS,
                 READ_REPOSITORY_FILE_OUTLINE,
                 SEARCH_REPOSITORY_CANDIDATES,
                 FIND_CLASS_REFERENCES,
                 FIND_FLOW_CONTEXT -> true;
            default -> false;
        };
    }

    AnalysisEvidenceSection capture(
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        return switch (toolName) {
            case READ_REPOSITORY_FILE -> captureGitLabFile(rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILE_CHUNK -> captureGitLabFileChunk(rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILE_CHUNKS -> captureGitLabFileChunks(rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILE_OUTLINE -> captureGitLabFileOutline(toolCallId, rawArguments, rawResult, sessionEvidence);
            case SEARCH_REPOSITORY_CANDIDATES -> captureGitLabSearch(toolCallId, rawArguments, rawResult, sessionEvidence);
            case FIND_CLASS_REFERENCES -> captureGitLabClassReferences(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    sessionEvidence
            );
            case FIND_FLOW_CONTEXT -> captureGitLabFlowContext(toolCallId, rawArguments, rawResult, sessionEvidence);
            default -> null;
        };
    }

    private AnalysisEvidenceSection captureGitLabFile(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileToolResponse.class);
            return sessionEvidence.upsertItem(
                    GITLAB_PROVIDER,
                    GITLAB_FETCHED_CODE_CATEGORY,
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    GITLAB_FILE_ORDER_NAMESPACE,
                    GITLAB_FILE_FALLBACK_KEY,
                    buildGitLabFileItem(
                            response.projectName(),
                            response.filePath(),
                            toolReason(rawArguments),
                            response.content(),
                            null
                    ),
                    this::keepExistingGitLabFileItem
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool file result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunk(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunkToolResponse.class);
            return sessionEvidence.upsertItem(
                    GITLAB_PROVIDER,
                    GITLAB_FETCHED_CODE_CATEGORY,
                    gitLabFileKey(response.group(), response.projectName(), response.branch(), response.filePath()),
                    GITLAB_FILE_ORDER_NAMESPACE,
                    GITLAB_FILE_FALLBACK_KEY,
                    buildGitLabFileItem(
                            response.projectName(),
                            response.filePath(),
                            toolReason(rawArguments),
                            response.content(),
                            response.returnedStartLine()
                    ),
                    this::keepExistingGitLabFileItem
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool chunk result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunks(
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileChunksToolResponse.class);
            var reason = toolReason(rawArguments);
            AnalysisEvidenceSection updatedSection = null;

            for (var chunk : safeList(response.chunks())) {
                if (chunk == null) {
                    continue;
                }

                updatedSection = sessionEvidence.upsertItem(
                        GITLAB_PROVIDER,
                        GITLAB_FETCHED_CODE_CATEGORY,
                        gitLabFileKey(chunk.group(), chunk.projectName(), chunk.branch(), chunk.filePath()),
                        GITLAB_FILE_ORDER_NAMESPACE,
                        GITLAB_FILE_FALLBACK_KEY,
                        buildGitLabFileItem(
                                chunk.projectName(),
                                chunk.filePath(),
                                reason,
                                chunk.content(),
                                chunk.returnedStartLine()
                        ),
                        this::keepExistingGitLabFileItem
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
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFileOutlineToolResponse.class);
            var attributes = buildGitLabDiscoveryAttributes(READ_REPOSITORY_FILE_OUTLINE, rawArguments);
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

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, READ_REPOSITORY_FILE_OUTLINE),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(READ_REPOSITORY_FILE_OUTLINE),
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
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabSearchRepositoryCandidatesToolResponse.class);
            var candidates = safeList(response.candidates());
            var attributes = buildGitLabDiscoveryAttributes(SEARCH_REPOSITORY_CANDIDATES, rawArguments);
            addAttribute(attributes, "candidateCount", String.valueOf(candidates.size()));
            addJsonAttribute(attributes, "candidates", candidates);

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, SEARCH_REPOSITORY_CANDIDATES),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(SEARCH_REPOSITORY_CANDIDATES),
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
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabFindClassReferencesToolResponse.class);
            var groups = safeList(response.groups());
            var recommendedNextReads = safeList(response.recommendedNextReads());
            var attributes = buildGitLabDiscoveryAttributes(FIND_CLASS_REFERENCES, rawArguments);
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "searchedClass", response.searchedClass());
            addAttribute(attributes, "groupCount", String.valueOf(groups.size()));
            addAttribute(attributes, "candidateCount", String.valueOf(candidateCount(groups)));
            addAttribute(attributes, "recommendedNextReadCount", String.valueOf(recommendedNextReads.size()));
            addJsonAttribute(attributes, "searchKeywords", response.searchKeywords());
            addJsonAttribute(attributes, "groups", groups);
            addJsonAttribute(attributes, "recommendedNextReads", recommendedNextReads);

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, FIND_CLASS_REFERENCES),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(FIND_CLASS_REFERENCES),
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
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabFindFlowContextToolResponse.class);
            var groups = safeList(response.groups());
            var recommendedNextReads = safeList(response.recommendedNextReads());
            var attributes = buildGitLabDiscoveryAttributes(FIND_FLOW_CONTEXT, rawArguments);
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "groupCount", String.valueOf(groups.size()));
            addAttribute(attributes, "candidateCount", String.valueOf(candidateCount(groups)));
            addAttribute(attributes, "recommendedNextReadCount", String.valueOf(recommendedNextReads.size()));
            addJsonAttribute(attributes, "groups", groups);
            addJsonAttribute(attributes, "recommendedNextReads", recommendedNextReads);

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, FIND_FLOW_CONTEXT),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(FIND_FLOW_CONTEXT),
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

    private boolean keepExistingGitLabFileItem(
            AnalysisEvidenceItem current,
            AnalysisEvidenceItem candidate
    ) {
        return !isChunkItem(current) && isChunkItem(candidate);
    }

    private boolean isChunkItem(AnalysisEvidenceItem item) {
        return item.attributes().stream()
                .anyMatch(attribute -> "startLine".equals(attribute.name()));
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
            case SEARCH_REPOSITORY_CANDIDATES -> "GitLab search candidates";
            case READ_REPOSITORY_FILE_OUTLINE -> "GitLab file outline";
            case FIND_CLASS_REFERENCES -> "GitLab class references";
            case FIND_FLOW_CONTEXT -> "GitLab flow context";
            default -> "GitLab tool result";
        };
    }

    private String safeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
