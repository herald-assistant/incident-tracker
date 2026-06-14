package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class GitLabEndpointUseCaseContextService {

    private final GitLabEndpointUseCaseEndpointResolver endpointResolver;
    private final GitLabRepositoryPort repositoryPort;
    private final GitLabEndpointUseCaseTraversalService traversalService;

    public GitLabEndpointUseCaseContextService(
            GitLabEndpointUseCaseEndpointResolver endpointResolver,
            GitLabRepositoryPort repositoryPort,
            GitLabEndpointUseCaseTraversalService traversalService
    ) {
        this.endpointResolver = endpointResolver;
        this.repositoryPort = repositoryPort;
        this.traversalService = traversalService;
    }

    public static GitLabEndpointUseCaseContextService createDefault(GitLabRepositoryPort repositoryPort) {
        return createDefault(repositoryPort, new GitLabRepositoryEndpointService(repositoryPort));
    }

    public static GitLabEndpointUseCaseContextService createDefault(
            GitLabRepositoryPort repositoryPort,
            GitLabRepositoryEndpointService endpointService
    ) {
        return new GitLabEndpointUseCaseContextService(
                new GitLabEndpointUseCaseEndpointResolver(endpointService),
                repositoryPort,
                new GitLabEndpointUseCaseTraversalService()
        );
    }

    public GitLabEndpointUseCaseContextResult buildContext(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var endpointResolution = endpointResolver.resolve(group, branch, request);
        if (endpointResolution.status() != GitLabEndpointUseCaseEndpointResolutionStatus.RESOLVED) {
            return unresolvedEndpointResult(endpointResolution, request);
        }

        var session = new GitLabEndpointUseCaseSourceSession(
                repositoryPort,
                endpointResolution.repository(),
                GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES,
                GitLabEndpointUseCaseSourceSession.DEFAULT_MAX_CHARACTERS_PER_FILE
        );
        return traversalService.traverse(
                session,
                endpointResolution.endpoint(),
                GitLabEndpointUseCaseLimits.forRequest(request)
        );
    }

    private GitLabEndpointUseCaseContextResult unresolvedEndpointResult(
            GitLabEndpointUseCaseEndpointResolution endpointResolution,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var repository = endpointResolution.repository() != null
                ? endpointResolution.repository()
                : repositoryContext(request);
        var limitations = new ArrayList<>(endpointResolution.limitations());
        if (!endpointResolution.candidates().isEmpty()) {
            limitations.add("Endpoint candidates are returned as candidate controller files; choose endpointId to continue traversal.");
        }
        return new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                candidateFiles(endpointResolution.candidates()),
                List.of(),
                List.of(new GitLabEndpointUseCaseUnresolvedReference(
                        endpointSelector(request),
                        null,
                        "Endpoint could not be resolved before use-case traversal: " + endpointResolution.status() + ".",
                        List.of("endpointId", "httpMethod", "endpointPath"),
                        endpointResolution.candidates().stream()
                                .map(GitLabEndpointUseCaseEndpointContext::endpointId)
                                .filter(Objects::nonNull)
                                .toList()
                )),
                limitations,
                suggestedReads(repository, endpointResolution.candidates()),
                GitLabEndpointUseCaseLimits.forRequest(request),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private GitLabEndpointUseCaseRepositoryContext repositoryContext(GitLabEndpointUseCaseContextRequest request) {
        return new GitLabEndpointUseCaseRepositoryContext(
                null,
                request != null ? request.projectName() : null,
                null
        );
    }

    private List<GitLabEndpointUseCaseFileCandidate> candidateFiles(
            List<GitLabEndpointUseCaseEndpointContext> candidates
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.filePath() != null)
                .map(candidate -> new GitLabEndpointUseCaseFileCandidate(
                        candidate.filePath(),
                        GitLabEndpointUseCaseFileRole.CONTROLLER,
                        1,
                        candidate.handlerMethod() != null ? List.of(candidate.handlerMethod()) : List.of(),
                        "Endpoint candidate returned by repository endpoint inventory.",
                        GitLabEndpointUseCaseConfidence.LOW
                ))
                .toList();
    }

    private List<String> suggestedReads(
            GitLabEndpointUseCaseRepositoryContext repository,
            List<GitLabEndpointUseCaseEndpointContext> candidates
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.filePath() != null)
                .map(candidate -> "%s:%s via gitlab_read_repository_file_outline".formatted(
                        repository != null && repository.projectName() != null ? repository.projectName() : "<project>",
                        candidate.filePath()
                ))
                .distinct()
                .toList();
    }

    private String endpointSelector(GitLabEndpointUseCaseContextRequest request) {
        if (request == null) {
            return null;
        }
        if (request.endpointId() != null) {
            return request.endpointId();
        }
        if (request.httpMethod() != null || request.endpointPath() != null) {
            return (request.httpMethod() != null ? request.httpMethod() : "<method>")
                    + " "
                    + (request.endpointPath() != null ? request.endpointPath() : "<path>");
        }
        return null;
    }
}
