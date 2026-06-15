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
import pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabBuildEndpointUseCaseContextToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindClassReferencesToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindFlowContextToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabListAvailableRepositoriesToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabListRepositoryEndpointsToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileOutlineToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFilesByPathToolResponse;
import pl.mkn.incidenttracker.agenttools.gitlab.mcp.GitLabToolDtos.GitLabSearchRepositoryCandidatesToolResponse;
import pl.mkn.incidenttracker.common.JsonPayloadReader;

import java.util.ArrayList;
import java.util.List;

import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.FIND_CLASS_REFERENCES;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.FIND_FLOW_CONTEXT;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.LIST_AVAILABLE_REPOSITORIES;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_ENDPOINTS;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNK;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE;
import static pl.mkn.incidenttracker.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH;
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
                 READ_REPOSITORY_FILES_BY_PATH,
                 READ_REPOSITORY_FILE_CHUNK,
                 READ_REPOSITORY_FILE_CHUNKS,
                 READ_REPOSITORY_FILE_OUTLINE,
                 LIST_AVAILABLE_REPOSITORIES,
                 LIST_REPOSITORY_ENDPOINTS,
                 BUILD_ENDPOINT_USE_CASE_CONTEXT,
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
            case READ_REPOSITORY_FILE -> captureGitLabFile(toolCallId, rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILES_BY_PATH -> captureGitLabFilesByPath(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    sessionEvidence
            );
            case READ_REPOSITORY_FILE_CHUNK -> captureGitLabFileChunk(toolCallId, rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILE_CHUNKS -> captureGitLabFileChunks(toolCallId, rawArguments, rawResult, sessionEvidence);
            case READ_REPOSITORY_FILE_OUTLINE -> captureGitLabFileOutline(toolCallId, rawArguments, rawResult, sessionEvidence);
            case LIST_AVAILABLE_REPOSITORIES -> captureGitLabAvailableRepositories(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    sessionEvidence
            );
            case LIST_REPOSITORY_ENDPOINTS -> captureGitLabRepositoryEndpoints(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    sessionEvidence
            );
            case BUILD_ENDPOINT_USE_CASE_CONTEXT -> captureGitLabEndpointUseCaseContext(
                    toolCallId,
                    rawArguments,
                    rawResult,
                    sessionEvidence
            );
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
            String toolCallId,
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
                            toolCallId,
                            READ_REPOSITORY_FILE,
                            rawArguments,
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

    private AnalysisEvidenceSection captureGitLabFilesByPath(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabReadRepositoryFilesByPathToolResponse.class);
            var reason = toolReason(rawArguments);
            AnalysisEvidenceSection updatedSection = null;

            for (var file : safeList(response.files())) {
                if (file == null || StringUtils.hasText(file.error())) {
                    continue;
                }

                updatedSection = sessionEvidence.upsertItem(
                        GITLAB_PROVIDER,
                        GITLAB_FETCHED_CODE_CATEGORY,
                        gitLabFileKey(file.group(), file.projectName(), file.branch(), file.filePath()),
                        GITLAB_FILE_ORDER_NAMESPACE,
                        GITLAB_FILE_FALLBACK_KEY,
                        buildGitLabFileItem(
                                file.projectName(),
                                file.filePath(),
                                reason,
                                toolCallId,
                                READ_REPOSITORY_FILES_BY_PATH,
                                rawArguments,
                                file.content(),
                                null
                        ),
                        this::keepExistingGitLabFileItem
                );
            }

            return updatedSection;
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab tool files-by-path result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabFileChunk(
            String toolCallId,
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
                            toolCallId,
                            READ_REPOSITORY_FILE_CHUNK,
                            rawArguments,
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
            String toolCallId,
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
                                toolCallId,
                                READ_REPOSITORY_FILE_CHUNKS,
                                rawArguments,
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
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    READ_REPOSITORY_FILE_OUTLINE,
                    rawArguments
            );
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

    private AnalysisEvidenceSection captureGitLabAvailableRepositories(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabListAvailableRepositoriesToolResponse.class);
            var repositories = safeList(response.repositories());
            var codeSearchScopes = safeList(response.codeSearchScopes());
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    LIST_AVAILABLE_REPOSITORIES,
                    rawArguments
            );
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "repositoryCount", String.valueOf(repositories.size()));
            addAttribute(attributes, "codeSearchScopeCount", String.valueOf(codeSearchScopes.size()));
            addJsonAttribute(attributes, "repositories", repositories);
            addJsonAttribute(attributes, "codeSearchScopes", codeSearchScopes);

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, LIST_AVAILABLE_REPOSITORIES),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(LIST_AVAILABLE_REPOSITORIES),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab available repositories result. reason={}", exception.getMessage(), exception);
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
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    SEARCH_REPOSITORY_CANDIDATES,
                    rawArguments
            );
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

    private AnalysisEvidenceSection captureGitLabRepositoryEndpoints(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabListRepositoryEndpointsToolResponse.class);
            var endpoints = safeList(response.endpoints());
            var limitations = safeList(response.limitations());
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    LIST_REPOSITORY_ENDPOINTS,
                    rawArguments
            );
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "projectName", response.projectName());
            addAttribute(attributes, "branch", response.branch());
            addAttribute(attributes, "endpointCount", String.valueOf(endpoints.size()));
            addAttribute(attributes, "candidateFileCount", String.valueOf(response.candidateFileCount()));
            addAttribute(attributes, "scannedFileCount", String.valueOf(response.scannedFileCount()));
            addAttribute(attributes, "scannedFileLimitReached", String.valueOf(response.scannedFileLimitReached()));
            addJsonAttribute(attributes, "endpoints", endpoints);
            addJsonAttribute(attributes, "limitations", limitations);

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, LIST_REPOSITORY_ENDPOINTS),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(LIST_REPOSITORY_ENDPOINTS),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab repository endpoints result. reason={}", exception.getMessage(), exception);
            return null;
        }
    }

    private AnalysisEvidenceSection captureGitLabEndpointUseCaseContext(
            String toolCallId,
            String rawArguments,
            String rawResult,
            CopilotToolEvidenceSessionStore.SessionToolEvidence sessionEvidence
    ) {
        try {
            var response = objectMapper.readValue(rawResult, GitLabBuildEndpointUseCaseContextToolResponse.class);
            var files = safeList(response.files());
            var relations = safeList(response.relations());
            var unresolved = safeList(response.unresolved());
            var limitations = safeList(response.limitations());
            var suggestedNextReads = safeList(response.suggestedNextReads());
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    BUILD_ENDPOINT_USE_CASE_CONTEXT,
                    rawArguments
            );
            addAttribute(attributes, "group", response.group());
            addAttribute(attributes, "projectName", response.projectName());
            addAttribute(attributes, "branch", response.branch());
            if (response.endpoint() != null) {
                addAttribute(attributes, "endpointId", response.endpoint().endpointId());
                addAttribute(attributes, "endpointPath", response.endpoint().path());
                addJsonAttribute(attributes, "httpMethods", response.endpoint().httpMethods());
                addAttribute(attributes, "controllerClass", response.endpoint().controllerClass());
                addAttribute(attributes, "handlerMethod", response.endpoint().handlerMethod());
                addAttribute(attributes, "endpointFilePath", response.endpoint().filePath());
            }
            addAttribute(attributes, "fileCount", String.valueOf(files.size()));
            addAttribute(attributes, "relationCount", String.valueOf(relations.size()));
            addAttribute(attributes, "unresolvedCount", String.valueOf(unresolved.size()));
            addAttribute(attributes, "limitationCount", String.valueOf(limitations.size()));
            addAttribute(attributes, "suggestedNextReadCount", String.valueOf(suggestedNextReads.size()));
            addAttribute(attributes, "confidence", response.confidence() != null ? response.confidence().name() : null);
            addJsonAttribute(attributes, "files", files);
            addJsonAttribute(attributes, "relations", relations);
            addJsonAttribute(attributes, "unresolved", unresolved);
            addJsonAttribute(attributes, "limitations", limitations);
            addJsonAttribute(attributes, "suggestedNextReads", suggestedNextReads);
            addJsonAttribute(attributes, "limits", response.limits());

            return sessionEvidence.appendItem(
                    GITLAB_PROVIDER,
                    GITLAB_DISCOVERY_CATEGORY,
                    discoveryKey(toolCallId, BUILD_ENDPOINT_USE_CASE_CONTEXT),
                    GITLAB_DISCOVERY_ORDER_NAMESPACE,
                    GITLAB_DISCOVERY_FALLBACK_KEY,
                    new AnalysisEvidenceItem(
                            gitLabDiscoveryTitle(BUILD_ENDPOINT_USE_CASE_CONTEXT),
                            List.copyOf(attributes)
                    )
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse GitLab endpoint use case context result. reason={}", exception.getMessage(), exception);
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
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    FIND_CLASS_REFERENCES,
                    rawArguments
            );
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
            var attributes = buildGitLabDiscoveryAttributes(
                    toolCallId,
                    FIND_FLOW_CONTEXT,
                    rawArguments
            );
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
            String toolCallId,
            String toolName,
            String rawArguments,
            String content,
            Integer startLine
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "filePath", filePath);
        addAttribute(attributes, "reason", reason);
        addToolTechnicalAttributes(attributes, toolCallId, toolName, rawArguments);
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

    private List<AnalysisEvidenceAttribute> buildGitLabDiscoveryAttributes(
            String toolCallId,
            String toolName,
            String rawArguments
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addAttribute(attributes, "reason", toolReason(rawArguments));
        addToolTechnicalAttributes(attributes, toolCallId, toolName, rawArguments);
        return attributes;
    }

    private void addToolTechnicalAttributes(
            List<AnalysisEvidenceAttribute> attributes,
            String toolCallId,
            String toolName,
            String rawArguments
    ) {
        addAttribute(attributes, "toolCallId", toolCallId);
        addAttribute(attributes, "toolName", toolName);
        addAttribute(
                attributes,
                "toolArguments",
                payloadReader.prettyPayload(payloadReader.readJsonNode(rawArguments), rawArguments, "")
        );
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
            case LIST_AVAILABLE_REPOSITORIES -> "GitLab available repositories";
            case LIST_REPOSITORY_ENDPOINTS -> "GitLab repository endpoints";
            case BUILD_ENDPOINT_USE_CASE_CONTEXT -> "GitLab endpoint use case context";
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
