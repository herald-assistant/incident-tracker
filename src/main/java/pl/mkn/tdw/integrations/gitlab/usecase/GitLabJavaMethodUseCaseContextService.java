package pl.mkn.tdw.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

@Service
public class GitLabJavaMethodUseCaseContextService {

    private final GitLabRepositoryPort repositoryPort;
    private final GitLabJavaMethodUseCaseEntryResolver entryResolver;
    private final GitLabEndpointUseCaseTraversalService traversalService;

    public GitLabJavaMethodUseCaseContextService(
            GitLabRepositoryPort repositoryPort,
            GitLabJavaMethodUseCaseEntryResolver entryResolver,
            GitLabEndpointUseCaseTraversalService traversalService
    ) {
        this.repositoryPort = repositoryPort;
        this.entryResolver = entryResolver;
        this.traversalService = traversalService;
    }

    public static GitLabJavaMethodUseCaseContextService createDefault(GitLabRepositoryPort repositoryPort) {
        return new GitLabJavaMethodUseCaseContextService(
                repositoryPort,
                new GitLabJavaMethodUseCaseEntryResolver(),
                new GitLabEndpointUseCaseTraversalService()
        );
    }

    public GitLabJavaMethodUseCaseContextResult buildContext(
            String group,
            String branch,
            GitLabJavaMethodUseCaseContextRequest request
    ) {
        var repository = repositoryContext(group, branch, request);
        var session = new GitLabEndpointUseCaseSourceSession(
                repositoryPort,
                repository,
                GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES,
                GitLabEndpointUseCaseSourceSession.DEFAULT_MAX_CHARACTERS_PER_FILE
        );
        var entryMethod = entryResolver.resolve(session, request);
        if (!entryMethod.resolved()) {
            return unresolvedEntryResult(repository, session, request, entryMethod);
        }

        var traversalResult = traversalService.traverseFromMethod(
                session,
                entryMethod,
                GitLabJavaMethodUseCaseContextLimits.fromSession(request, session, false, false)
        );
        return fromTraversalResult(entryMethod, traversalResult);
    }

    private GitLabJavaMethodUseCaseContextResult unresolvedEntryResult(
            GitLabEndpointUseCaseRepositoryContext repository,
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaMethodUseCaseContextRequest request,
            GitLabJavaMethodUseCaseEntryMethod entryMethod
    ) {
        var unresolved = new GitLabEndpointUseCaseUnresolvedReference(
                symbol(entryMethod.requestedClassName(), entryMethod.requestedMethodName()),
                request != null ? request.filePath() : null,
                "Java entry method could not be resolved before use-case traversal: "
                        + entryMethod.status() + ".",
                request != null
                        ? GitLabEndpointUseCaseModelSupport.copyStrings(List.of(
                        request.className() != null ? request.className() : "",
                        request.methodName() != null ? request.methodName() : ""
                ))
                        : List.of(),
                entryMethod.candidates().stream()
                        .map(GitLabJavaMethodUseCaseEntryCandidate::filePath)
                        .distinct()
                        .toList()
        );
        var files = candidateFiles(entryMethod.candidates(), request != null ? request.maxResults() : 0);
        return new GitLabJavaMethodUseCaseContextResult(
                repository,
                entryMethod,
                files,
                List.of(),
                List.of(unresolved),
                entryMethod.limitations(),
                suggestedNextReads(repository, files),
                GitLabJavaMethodUseCaseContextLimits.fromSession(request, session, false, false),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private GitLabJavaMethodUseCaseContextResult fromTraversalResult(
            GitLabJavaMethodUseCaseEntryMethod entryMethod,
            GitLabEndpointUseCaseContextResult traversalResult
    ) {
        return new GitLabJavaMethodUseCaseContextResult(
                traversalResult.repository(),
                entryMethod,
                traversalResult.files(),
                traversalResult.relations(),
                traversalResult.unresolved(),
                traversalResult.limitations(),
                traversalResult.suggestedNextReads(),
                GitLabJavaMethodUseCaseContextLimits.fromEndpointLimits(traversalResult.limits()),
                traversalResult.confidence()
        );
    }

    private GitLabEndpointUseCaseRepositoryContext repositoryContext(
            String group,
            String branch,
            GitLabJavaMethodUseCaseContextRequest request
    ) {
        return new GitLabEndpointUseCaseRepositoryContext(
                group,
                request != null ? request.projectName() : null,
                branch
        );
    }

    private List<GitLabEndpointUseCaseFileCandidate> candidateFiles(
            List<GitLabJavaMethodUseCaseEntryCandidate> candidates,
            int maxResults
    ) {
        var limit = maxResults > 0 ? maxResults : GitLabJavaMethodUseCaseContextRequest.DEFAULT_MAX_RESULTS;
        return candidates.stream()
                .filter(candidate -> candidate.filePath() != null)
                .limit(limit)
                .map(candidate -> new GitLabEndpointUseCaseFileCandidate(
                        candidate.filePath(),
                        roleForCandidate(candidate),
                        rolePriority(roleForCandidate(candidate)),
                        candidate.methodName() != null ? List.of(candidate.methodName()) : List.of(),
                        candidate.reason(),
                        candidate.confidence()
                ))
                .toList();
    }

    private GitLabEndpointUseCaseFileRole roleForCandidate(GitLabJavaMethodUseCaseEntryCandidate candidate) {
        var typeName = candidate.declaringTypeQualifiedName() != null
                ? candidate.declaringTypeQualifiedName()
                : candidate.declaringTypeSimpleName();
        var simpleName = simpleName(typeName);
        if (simpleName == null) {
            return GitLabEndpointUseCaseFileRole.UNKNOWN;
        }
        if (simpleName.endsWith("Service") || simpleName.endsWith("UseCase")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE;
        }
        if (simpleName.endsWith("Mapper")) {
            return GitLabEndpointUseCaseFileRole.MAPPER;
        }
        if (simpleName.contains("RepositoryPort")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_PORT;
        }
        if (simpleName.contains("Repository")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION;
        }
        if (simpleName.endsWith("Port")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_PORT;
        }
        return GitLabEndpointUseCaseFileRole.UNKNOWN;
    }

    private int rolePriority(GitLabEndpointUseCaseFileRole role) {
        return switch (role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN) {
            case CONTROLLER, OPENAPI_CONTRACT, API_INTERFACE -> 1;
            case USE_CASE_PORT, USE_CASE_SERVICE -> 2;
            case REPOSITORY_PORT, REPOSITORY_IMPLEMENTATION, SPRING_DATA_REPOSITORY -> 4;
            case MAPPER -> 5;
            case DOMAIN_MODEL -> 6;
            case WEB_MODEL, PROJECTION -> 7;
            case CONFIGURATION, EXTERNAL_CLIENT -> 8;
            case UNKNOWN -> 9;
        };
    }

    private List<String> suggestedNextReads(
            GitLabEndpointUseCaseRepositoryContext repository,
            List<GitLabEndpointUseCaseFileCandidate> files
    ) {
        var projectName = repository != null && repository.projectName() != null
                ? repository.projectName()
                : "<project>";
        return files.stream()
                .map(file -> "%s:%s via gitlab_read_repository_file_outline".formatted(projectName, file.path()))
                .toList();
    }

    private String symbol(String typeName, String methodName) {
        var normalizedTypeName = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        var normalizedMethodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        if (normalizedTypeName == null) {
            return normalizedMethodName;
        }
        if (normalizedMethodName == null) {
            return normalizedTypeName;
        }
        return normalizedTypeName + "#" + normalizedMethodName;
    }

    private String simpleName(String typeName) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (normalized == null) {
            return null;
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }
}
