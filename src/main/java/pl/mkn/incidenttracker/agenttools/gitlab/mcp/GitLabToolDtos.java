package pl.mkn.incidenttracker.agenttools.gitlab.mcp;

import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseUnresolvedReference;

import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.ACTUAL_COPILOT_SESSION_ID;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.ANALYSIS_RUN_ID;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.COPILOT_SESSION_ID;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.CORRELATION_ID;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.ENVIRONMENT;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.GITLAB_BRANCH;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.GITLAB_GROUP;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.TOOL_CALL_ID;
import static pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys.TOOL_NAME;

public final class GitLabToolDtos {

    private GitLabToolDtos() {
    }

    public record GitLabFileChunkRequest(
            String projectName,
            String filePath,
            int startLine,
            int endLine,
            Integer maxCharacters
    ) {
    }

    public record GitLabFileChunkResult(
            String group,
            String projectName,
            String branch,
            String filePath,
            int requestedStartLine,
            int requestedEndLine,
            int returnedStartLine,
            int returnedEndLine,
            int totalLines,
            String content,
            boolean truncated,
            String inferredRole
    ) {
    }

    public record GitLabFindClassReferencesToolResponse(
            String group,
            String branch,
            String searchedClass,
            List<String> searchKeywords,
            List<GitLabFlowContextGroup> groups,
            List<String> recommendedNextReads
    ) {
    }

    public record GitLabFindFlowContextToolResponse(
            String group,
            String branch,
            List<GitLabFlowContextGroup> groups,
            List<String> recommendedNextReads
    ) {
    }

    public record GitLabFlowContextCandidate(
            String group,
            String projectName,
            String branch,
            String filePath,
            String matchReason,
            int matchScore,
            String inferredRole,
            String recommendedReadStrategy,
            String preview
    ) {
    }

    public record GitLabFlowContextGroup(
            String role,
            List<GitLabFlowContextCandidate> candidates
    ) {
    }

    public record GitLabFileContentResult(
            String group,
            String projectName,
            String branch,
            String filePath,
            String content,
            boolean truncated,
            String inferredRole,
            int returnedCharacters,
            Long sizeBytes,
            String contentSha256,
            String blobId,
            String commitId,
            String lastCommitId,
            String lastModifiedAt,
            String metadataStatus,
            String metadataError,
            String error
    ) {
    }

    public record GitLabListAvailableRepositoriesToolResponse(
            String group,
            String branch,
            List<GitLabAvailableRepository> repositories,
            List<GitLabAvailableCodeSearchScope> codeSearchScopes
    ) {
        public GitLabListAvailableRepositoriesToolResponse {
            repositories = repositories != null ? List.copyOf(repositories) : List.of();
            codeSearchScopes = codeSearchScopes != null ? List.copyOf(codeSearchScopes) : List.of();
        }
    }

    public record GitLabListRepositoryEndpointsToolResponse(
            String group,
            String projectName,
            String branch,
            String endpointPathPrefix,
            String httpMethod,
            int candidateFileCount,
            int scannedFileCount,
            boolean scannedFileLimitReached,
            List<GitLabRepositoryEndpoint> endpoints,
            List<String> limitations
    ) {
        public GitLabListRepositoryEndpointsToolResponse {
            endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }
    }

    public record GitLabBuildEndpointUseCaseContextToolResponse(
            String group,
            String projectName,
            String branch,
            GitLabEndpointUseCaseEndpointContext endpoint,
            List<GitLabEndpointUseCaseFileCandidate> files,
            List<GitLabEndpointUseCaseRelation> relations,
            List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
            List<String> limitations,
            List<String> suggestedNextReads,
            GitLabEndpointUseCaseLimits limits,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        public GitLabBuildEndpointUseCaseContextToolResponse {
            files = files != null ? List.copyOf(files) : List.of();
            relations = relations != null ? List.copyOf(relations) : List.of();
            unresolved = unresolved != null ? List.copyOf(unresolved) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
            suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
            limits = limits != null ? limits : GitLabEndpointUseCaseLimits.defaults();
            confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
        }

        public static GitLabBuildEndpointUseCaseContextToolResponse from(GitLabEndpointUseCaseContextResult result) {
            var repository = result != null ? result.repository() : null;
            return new GitLabBuildEndpointUseCaseContextToolResponse(
                    repository != null ? repository.group() : null,
                    repository != null ? repository.projectName() : null,
                    repository != null ? repository.branch() : null,
                    result != null ? result.endpoint() : null,
                    result != null ? result.files() : List.of(),
                    result != null ? result.relations() : List.of(),
                    result != null ? result.unresolved() : List.of(),
                    result != null ? result.limitations() : List.of(),
                    result != null ? result.suggestedNextReads() : List.of(),
                    result != null ? result.limits() : GitLabEndpointUseCaseLimits.defaults(),
                    result != null ? result.confidence() : GitLabEndpointUseCaseConfidence.LOW
            );
        }
    }

    public record GitLabAvailableRepository(
            String repositoryId,
            String name,
            String summary,
            String projectName,
            String gitLabPath,
            List<String> aliases,
            String repositoryType,
            String lifecycleStatus,
            List<String> systems,
            List<String> boundedContexts,
            List<String> processes,
            List<String> integrations,
            List<String> relatedRepositoryIds,
            List<String> packagePrefixes,
            List<String> endpointPrefixes,
            List<String> modulePaths
    ) {
        public GitLabAvailableRepository {
            aliases = aliases != null ? List.copyOf(aliases) : List.of();
            systems = systems != null ? List.copyOf(systems) : List.of();
            boundedContexts = boundedContexts != null ? List.copyOf(boundedContexts) : List.of();
            processes = processes != null ? List.copyOf(processes) : List.of();
            integrations = integrations != null ? List.copyOf(integrations) : List.of();
            relatedRepositoryIds = relatedRepositoryIds != null ? List.copyOf(relatedRepositoryIds) : List.of();
            packagePrefixes = packagePrefixes != null ? List.copyOf(packagePrefixes) : List.of();
            endpointPrefixes = endpointPrefixes != null ? List.copyOf(endpointPrefixes) : List.of();
            modulePaths = modulePaths != null ? List.copyOf(modulePaths) : List.of();
        }
    }

    public record GitLabAvailableCodeSearchScope(
            String scopeId,
            String name,
            String scopeType,
            String lifecycleStatus,
            GitLabAvailableCodeSearchTarget target,
            List<String> useFor,
            List<GitLabAvailableCodeSearchRepository> repositories,
            List<String> projectNames,
            List<String> packagePrefixes,
            List<String> classHints,
            List<String> endpointHints,
            List<String> queueTopicHints,
            GitLabAvailableCodeSearchTraversal traversal
    ) {
        public GitLabAvailableCodeSearchScope {
            target = target != null ? target : GitLabAvailableCodeSearchTarget.empty();
            useFor = useFor != null ? List.copyOf(useFor) : List.of();
            repositories = repositories != null ? List.copyOf(repositories) : List.of();
            projectNames = projectNames != null ? List.copyOf(projectNames) : List.of();
            packagePrefixes = packagePrefixes != null ? List.copyOf(packagePrefixes) : List.of();
            classHints = classHints != null ? List.copyOf(classHints) : List.of();
            endpointHints = endpointHints != null ? List.copyOf(endpointHints) : List.of();
            queueTopicHints = queueTopicHints != null ? List.copyOf(queueTopicHints) : List.of();
            traversal = traversal != null ? traversal : GitLabAvailableCodeSearchTraversal.empty();
        }
    }

    public record GitLabAvailableCodeSearchTarget(
            String type,
            String id
    ) {
        public static GitLabAvailableCodeSearchTarget empty() {
            return new GitLabAvailableCodeSearchTarget(null, null);
        }
    }

    public record GitLabAvailableCodeSearchRepository(
            String repositoryId,
            String role,
            Integer priority,
            List<String> projectNames,
            List<String> moduleIds,
            String reason,
            List<String> readFor
    ) {
        public GitLabAvailableCodeSearchRepository {
            projectNames = projectNames != null ? List.copyOf(projectNames) : List.of();
            moduleIds = moduleIds != null ? List.copyOf(moduleIds) : List.of();
            readFor = readFor != null ? List.copyOf(readFor) : List.of();
        }
    }

    public record GitLabAvailableCodeSearchTraversal(
            List<String> rules,
            List<String> expandWhen
    ) {
        public GitLabAvailableCodeSearchTraversal {
            rules = rules != null ? List.copyOf(rules) : List.of();
            expandWhen = expandWhen != null ? List.copyOf(expandWhen) : List.of();
        }

        public static GitLabAvailableCodeSearchTraversal empty() {
            return new GitLabAvailableCodeSearchTraversal(List.of(), List.of());
        }
    }

    public record GitLabReadRepositoryFileChunksToolResponse(
            String group,
            String branch,
            List<GitLabFileChunkResult> chunks,
            boolean chunkCountTruncated,
            boolean totalCharacterLimitReached
    ) {
    }

    public record GitLabReadRepositoryFilesByPathToolResponse(
            String group,
            String projectName,
            String branch,
            int requestedFileCount,
            int processedFileCount,
            int returnedFileCount,
            int failedFileCount,
            int totalReturnedCharacters,
            boolean fileCountTruncated,
            boolean totalCharacterLimitReached,
            List<GitLabFileContentResult> files
    ) {
        public GitLabReadRepositoryFilesByPathToolResponse {
            files = files != null ? List.copyOf(files) : List.of();
        }
    }

    public record GitLabReadRepositoryFileChunkToolResponse(
            String group,
            String projectName,
            String branch,
            String filePath,
            int requestedStartLine,
            int requestedEndLine,
            int returnedStartLine,
            int returnedEndLine,
            int totalLines,
            String content,
            boolean truncated
    ) {
    }

    public record GitLabReadRepositoryFileOutlineToolResponse(
            String group,
            String projectName,
            String branch,
            String filePath,
            String packageName,
            List<String> imports,
            List<String> classes,
            List<String> annotations,
            List<String> methodSignatures,
            String inferredRole,
            boolean truncated
    ) {
    }

    public record GitLabReadRepositoryFileToolResponse(
            String group,
            String projectName,
            String branch,
            String filePath,
            String content,
            boolean truncated
    ) {
    }

    public record GitLabSearchRepositoryCandidatesToolResponse(
            List<GitLabRepositoryFileCandidate> candidates
    ) {
    }

    public record GitLabToolScope(
            String correlationId,
            String group,
            String branch,
            String environment,
            String analysisRunId,
            String copilotSessionId,
            String toolCallId,
            String toolName
    ) {

        public static GitLabToolScope from(ToolContext toolContext) {
            if (toolContext == null || toolContext.getContext() == null) {
                throw new IllegalStateException("Missing Copilot tool context; GitLab tools require session-bound scope.");
            }

            var context = toolContext.getContext();

            return new GitLabToolScope(
                    required(
                            context,
                            CORRELATION_ID,
                            "Missing correlationId in Copilot tool context; GitLab tools require session-bound correlationId."
                    ),
                    required(
                            context,
                            GITLAB_GROUP,
                            "Missing gitLabGroup in Copilot tool context; GitLab tools require session-bound group."
                    ),
                    required(
                            context,
                            GITLAB_BRANCH,
                            "Missing gitLabBranch in Copilot tool context; GitLab tools require resolved session branch."
                    ),
                    optional(context, ENVIRONMENT),
                    optional(context, ANALYSIS_RUN_ID),
                    firstNonBlank(optional(context, ACTUAL_COPILOT_SESSION_ID), optional(context, COPILOT_SESSION_ID)),
                    optional(context, TOOL_CALL_ID),
                    optional(context, TOOL_NAME)
            );
        }

        private static String required(Map<String, Object> context, String key, String message) {
            var value = context.get(key);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalStateException(message);
            }
            return value.toString();
        }

        private static String optional(Map<String, Object> context, String key) {
            var value = context.get(key);
            return value != null && !value.toString().isBlank() ? value.toString() : null;
        }

        private static String firstNonBlank(String primary, String fallback) {
            if (primary != null && !primary.isBlank()) {
                return primary;
            }
            return fallback;
        }
    }
}
