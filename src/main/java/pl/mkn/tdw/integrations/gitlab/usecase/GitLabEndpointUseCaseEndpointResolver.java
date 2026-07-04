package pl.mkn.tdw.integrations.gitlab.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitLabEndpointUseCaseEndpointResolver {

    private static final int MAX_NOT_FOUND_CANDIDATES = 10;

    private final GitLabRepositoryEndpointService endpointService;

    public GitLabEndpointUseCaseEndpointResolution resolve(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request
    ) {
        if (request == null) {
            return invalid(null, "Endpoint use case context request is required.");
        }

        var repository = new GitLabEndpointUseCaseRepositoryContext(
                group,
                request.projectName(),
                branch
        );
        var validationLimitations = validate(repository, request);
        if (!validationLimitations.isEmpty()) {
            return new GitLabEndpointUseCaseEndpointResolution(
                    GitLabEndpointUseCaseEndpointResolutionStatus.INVALID_REQUEST,
                    repository,
                    null,
                    List.of(),
                    validationLimitations
            );
        }

        var endpointList = endpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                repository.group(),
                repository.projectName(),
                repository.branch(),
                request.endpointId() == null ? request.endpointPath() : null,
                request.endpointId() == null ? request.httpMethod() : null,
                null
        ));
        var limitations = new ArrayList<String>(endpointList.limitations());
        var matches = endpointList.endpoints().stream()
                .filter(endpoint -> matches(endpoint, request))
                .sorted(Comparator.comparing(GitLabRepositoryEndpoint::endpointId, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (matches.isEmpty()) {
            limitations.add(endpointNotFoundMessage(request));
            return new GitLabEndpointUseCaseEndpointResolution(
                    GitLabEndpointUseCaseEndpointResolutionStatus.ENDPOINT_NOT_FOUND,
                    repository,
                    null,
                    endpointList.endpoints().stream()
                            .limit(MAX_NOT_FOUND_CANDIDATES)
                            .map(this::toContext)
                            .toList(),
                    limitations
            );
        }

        if (matches.size() > 1) {
            limitations.add("More than one endpoint matched the requested selector; choose endpointId for an exact start point.");
            return new GitLabEndpointUseCaseEndpointResolution(
                    GitLabEndpointUseCaseEndpointResolutionStatus.AMBIGUOUS_ENDPOINT,
                    repository,
                    null,
                    matches.stream()
                            .map(this::toContext)
                            .toList(),
                    limitations
            );
        }

        var endpoint = matches.get(0);
        limitations.addAll(endpoint.limitations());
        return new GitLabEndpointUseCaseEndpointResolution(
                GitLabEndpointUseCaseEndpointResolutionStatus.RESOLVED,
                repository,
                toContext(endpoint),
                List.of(),
                limitations
        );
    }

    private GitLabEndpointUseCaseEndpointResolution invalid(
            GitLabEndpointUseCaseRepositoryContext repository,
            String limitation
    ) {
        return new GitLabEndpointUseCaseEndpointResolution(
                GitLabEndpointUseCaseEndpointResolutionStatus.INVALID_REQUEST,
                repository,
                null,
                List.of(),
                List.of(limitation)
        );
    }

    private List<String> validate(
            GitLabEndpointUseCaseRepositoryContext repository,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var limitations = new ArrayList<String>();
        if (!StringUtils.hasText(repository.group())) {
            limitations.add("GitLab group is required in hidden tool context.");
        }
        if (!StringUtils.hasText(repository.projectName())) {
            limitations.add("projectName is required.");
        }
        if (!StringUtils.hasText(repository.branch())) {
            limitations.add("GitLab branch is required in hidden tool context.");
        }
        if (!StringUtils.hasText(request.endpointId())
                && (!StringUtils.hasText(request.httpMethod()) || !StringUtils.hasText(request.endpointPath()))) {
            limitations.add("endpointId or httpMethod + endpointPath is required.");
        }
        return List.copyOf(limitations);
    }

    private boolean matches(GitLabRepositoryEndpoint endpoint, GitLabEndpointUseCaseContextRequest request) {
        if (StringUtils.hasText(request.endpointId())) {
            return request.endpointId().equals(endpoint.endpointId());
        }
        return (endpoint.httpMethods().contains(request.httpMethod()) || endpoint.httpMethods().contains("ANY"))
                && normalizePathForMatch(request.endpointPath()).equals(normalizePathForMatch(endpoint.path()));
    }

    private String endpointNotFoundMessage(GitLabEndpointUseCaseContextRequest request) {
        if (StringUtils.hasText(request.endpointId())) {
            return "Endpoint was not found by endpointId: " + request.endpointId();
        }
        return "Endpoint was not found by httpMethod + endpointPath: "
                + request.httpMethod() + " " + request.endpointPath();
    }

    private GitLabEndpointUseCaseEndpointContext toContext(GitLabRepositoryEndpoint endpoint) {
        return new GitLabEndpointUseCaseEndpointContext(
                endpoint.endpointId(),
                endpoint.httpMethods(),
                endpoint.path(),
                endpoint.pathExpression(),
                endpoint.controllerClass(),
                endpoint.handlerMethod(),
                endpoint.filePath(),
                endpoint.lineStart(),
                endpoint.lineEnd(),
                endpoint.requestTypes(),
                endpoint.responseTypes(),
                endpoint.annotations(),
                endpoint.documentation(),
                GitLabEndpointUseCaseConfidence.from(endpoint.confidence()),
                endpoint.limitations(),
                endpoint.suggestedNextReads()
        );
    }

    private String normalizePathForMatch(String path) {
        var normalized = GitLabEndpointUseCaseModelSupport.normalizeEndpointPath(path);
        if (normalized == null) {
            return "";
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
