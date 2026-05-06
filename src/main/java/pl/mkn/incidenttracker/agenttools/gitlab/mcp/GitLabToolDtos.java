package pl.mkn.incidenttracker.agenttools.gitlab.mcp;

import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;

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
            List<String> runtimeComponents,
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
            runtimeComponents = runtimeComponents != null ? List.copyOf(runtimeComponents) : List.of();
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
            String lifecycleStatus,
            List<String> targetSystems,
            List<String> targetRuntimeComponents,
            List<String> targetProcesses,
            List<String> targetBoundedContexts,
            List<String> useFor,
            List<GitLabAvailableCodeSearchRepository> repositories,
            List<String> projectNames,
            List<String> packagePrefixes,
            List<String> classHints,
            List<String> endpointHints,
            List<String> queueTopicHints,
            GitLabAvailableCodeSearchStrategy searchStrategy
    ) {
        public GitLabAvailableCodeSearchScope {
            targetSystems = targetSystems != null ? List.copyOf(targetSystems) : List.of();
            targetRuntimeComponents = targetRuntimeComponents != null ? List.copyOf(targetRuntimeComponents) : List.of();
            targetProcesses = targetProcesses != null ? List.copyOf(targetProcesses) : List.of();
            targetBoundedContexts = targetBoundedContexts != null ? List.copyOf(targetBoundedContexts) : List.of();
            useFor = useFor != null ? List.copyOf(useFor) : List.of();
            repositories = repositories != null ? List.copyOf(repositories) : List.of();
            projectNames = projectNames != null ? List.copyOf(projectNames) : List.of();
            packagePrefixes = packagePrefixes != null ? List.copyOf(packagePrefixes) : List.of();
            classHints = classHints != null ? List.copyOf(classHints) : List.of();
            endpointHints = endpointHints != null ? List.copyOf(endpointHints) : List.of();
            queueTopicHints = queueTopicHints != null ? List.copyOf(queueTopicHints) : List.of();
            searchStrategy = searchStrategy != null ? searchStrategy : GitLabAvailableCodeSearchStrategy.empty();
        }
    }

    public record GitLabAvailableCodeSearchRepository(
            String repositoryId,
            String role,
            Integer priority,
            List<String> projectNames,
            List<String> moduleIds,
            String reason
    ) {
        public GitLabAvailableCodeSearchRepository {
            projectNames = projectNames != null ? List.copyOf(projectNames) : List.of();
            moduleIds = moduleIds != null ? List.copyOf(moduleIds) : List.of();
        }
    }

    public record GitLabAvailableCodeSearchStrategy(
            List<String> priorityOrder,
            boolean includeGeneratedClients,
            boolean includeSharedLibraries,
            boolean includeDeploymentConfig,
            boolean includeDocumentation,
            List<String> notes
    ) {
        public GitLabAvailableCodeSearchStrategy {
            priorityOrder = priorityOrder != null ? List.copyOf(priorityOrder) : List.of();
            notes = notes != null ? List.copyOf(notes) : List.of();
        }

        public static GitLabAvailableCodeSearchStrategy empty() {
            return new GitLabAvailableCodeSearchStrategy(List.of(), false, false, false, false, List.of());
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
