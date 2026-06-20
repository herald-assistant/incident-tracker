package pl.mkn.incidenttracker.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseMethodCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FlowExplorerContextService {

    private final FlowExplorerRepositoryScopeService repositoryScopeService;
    private final GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService;
    private final FlowExplorerSnippetCardService snippetCardService;

    public FlowExplorerContextSnapshot buildContext(FlowExplorerContextRequest request) {
        var scope = repositoryScopeService.resolve(request.systemId(), request.branch());
        var endpointSelector = endpointSelector(request.endpointId(), scope.repositories());
        var repositories = selectedRepositories(scope.repositories(), endpointSelector.projectName());
        var limitations = new ArrayList<>(scope.limitations());
        var repositoryContexts = new ArrayList<FlowExplorerRepositoryContext>();
        GitLabEndpointUseCaseContextResult selectedResult = null;
        FlowExplorerRepositoryScopeRepository selectedRepository = null;
        var attemptedRepositoryCount = 0;

        if (repositories.isEmpty() && endpointSelector.projectName() != null) {
            limitations.add("Endpoint id targets repository " + endpointSelector.projectName()
                    + ", but it is not part of the resolved Flow Explorer repository scope.");
        }

        for (var repository : repositories) {
            attemptedRepositoryCount++;
            try {
                var result = gitLabEndpointUseCaseContextService.buildContext(
                        scope.gitLabGroup(),
                        scope.resolvedRef(),
                        new GitLabEndpointUseCaseContextRequest(
                                repository.projectName(),
                                endpointSelector.endpointId(),
                                request.httpMethod(),
                                request.endpointPath(),
                                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH,
                                GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_FILES
                        )
                );
                repositoryContexts.add(repositoryContext(repository, scope.resolvedRef(), true,
                        result.limitations()));
                limitations.addAll(prefixedLimitations(repository.projectName(), result.limitations()));
                if (selectedResult == null || result.endpoint() != null) {
                    selectedResult = result;
                    selectedRepository = repository;
                }
                if (result.endpoint() != null) {
                    break;
                }
            } catch (RuntimeException exception) {
                var limitation = "Repository " + repository.repositoryId()
                        + " endpoint use-case context failed: " + exception.getMessage();
                limitations.add(limitation);
                repositoryContexts.add(repositoryContext(repository, scope.resolvedRef(), true,
                        List.of(limitation)));
            }
        }

        for (var repository : scope.repositories()) {
            if (repositoryContexts.stream().noneMatch(context -> repository.repositoryId().equals(context.repositoryId()))) {
                repositoryContexts.add(repositoryContext(repository, scope.resolvedRef(), false, List.of()));
            }
        }
        var finalRepositoryContexts = markSelectedRepository(repositoryContexts, selectedRepository);

        var endpointResolved = selectedResult != null && selectedResult.endpoint() != null;
        var flowNodes = endpointResolved ? flowNodes(selectedResult.files()) : List.<FlowExplorerFlowNode>of();
        var relations = endpointResolved ? relations(selectedResult.relations()) : List.<FlowExplorerFlowRelation>of();
        var suggestedNextReads = endpointResolved ? selectedResult.suggestedNextReads() : List.<String>of();
        var snippetCardResult = endpointResolved
                ? snippetCards(scope.gitLabGroup(), scope.resolvedRef(), finalRepositoryContexts, flowNodes, request)
                : FlowExplorerSnippetCardResult.empty();
        limitations.addAll(snippetCardResult.limitations());
        var coverage = coverage(
                endpointResolved,
                scope.repositoryRefCount(),
                attemptedRepositoryCount,
                flowNodes,
                relations,
                snippetCardResult,
                selectedResult,
                limitations
        );

        return new FlowExplorerContextSnapshot(
                scope.system().id(),
                scope.system().name(),
                scope.requestedBranch(),
                scope.resolvedRef(),
                scope.gitLabGroup(),
                endpointSelector.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                endpointResolved ? endpoint(selectedResult.endpoint()) : null,
                finalRepositoryContexts,
                flowNodes,
                relations,
                snippetCardResult.cards(),
                distinct(limitations),
                suggestedNextReads,
                coverage
        );
    }

    private EndpointSelector endpointSelector(
            String endpointId,
            List<FlowExplorerRepositoryScopeRepository> repositories
    ) {
        if (!StringUtils.hasText(endpointId)) {
            return new EndpointSelector(null, null);
        }
        var trimmed = endpointId.trim();
        var matchingRepository = repositories.stream()
                .map(FlowExplorerRepositoryScopeRepository::projectName)
                .filter(StringUtils::hasText)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(projectName -> trimmed.regionMatches(true, 0, projectName + ":", 0, projectName.length() + 1))
                .findFirst()
                .orElse(null);
        if (matchingRepository == null) {
            return new EndpointSelector(null, trimmed);
        }
        return new EndpointSelector(
                matchingRepository,
                trimmed.substring(matchingRepository.length() + 1)
        );
    }

    private List<FlowExplorerRepositoryScopeRepository> selectedRepositories(
            List<FlowExplorerRepositoryScopeRepository> repositories,
            String projectName
    ) {
        if (!StringUtils.hasText(projectName)) {
            return repositories;
        }
        var normalizedProjectName = normalizeComparable(projectName);
        return repositories.stream()
                .filter(repository -> normalizeComparable(repository.projectName()).equals(normalizedProjectName))
                .toList();
    }

    private FlowExplorerRepositoryContext repositoryContext(
            FlowExplorerRepositoryScopeRepository repository,
            String resolvedRef,
            boolean attempted,
            List<String> limitations
    ) {
        return new FlowExplorerRepositoryContext(
                repository.repositoryId(),
                repository.projectName(),
                repository.projectPath(),
                resolvedRef,
                attempted,
                false,
                limitations
        );
    }

    private List<FlowExplorerRepositoryContext> markSelectedRepository(
            List<FlowExplorerRepositoryContext> repositories,
            FlowExplorerRepositoryScopeRepository selectedRepository
    ) {
        var selectedProjectName = selectedRepository != null ? selectedRepository.projectName() : null;
        return repositories.stream()
                .map(repository -> new FlowExplorerRepositoryContext(
                        repository.repositoryId(),
                        repository.projectName(),
                        repository.projectPath(),
                        repository.resolvedRef(),
                        repository.attempted(),
                        selectedProjectName != null && selectedProjectName.equals(repository.projectName()),
                        repository.limitations()
                ))
                .toList();
    }

    private FlowExplorerEndpointContext endpoint(GitLabEndpointUseCaseEndpointContext endpoint) {
        return new FlowExplorerEndpointContext(
                endpoint.endpointId(),
                endpoint.httpMethods(),
                endpoint.path(),
                endpoint.pathExpression(),
                endpoint.controllerClass(),
                endpoint.handlerMethod(),
                endpoint.filePath(),
                endpoint.lineStart(),
                endpoint.lineEnd(),
                confidence(endpoint.confidence())
        );
    }

    private List<FlowExplorerFlowNode> flowNodes(List<GitLabEndpointUseCaseFileCandidate> files) {
        return files.stream()
                .filter(file -> file.path() != null)
                .sorted(Comparator
                        .comparingInt(GitLabEndpointUseCaseFileCandidate::priority)
                .thenComparing(GitLabEndpointUseCaseFileCandidate::path))
                .map(file -> new FlowExplorerFlowNode(
                        file.path(),
                        file.role() != null ? file.role().name() : "UNKNOWN",
                        file.path(),
                        methods(file.methods()),
                        file.reason(),
                        confidence(file.confidence()),
                        List.of()
                ))
                .toList();
    }

    private List<FlowExplorerFlowMethod> methods(List<GitLabEndpointUseCaseMethodCandidate> methods) {
        return methods.stream()
                .filter(method -> method.methodName() != null)
                .sorted(Comparator
                        .comparingInt(GitLabEndpointUseCaseMethodCandidate::depth)
                        .thenComparingInt(GitLabEndpointUseCaseMethodCandidate::lineStart)
                        .thenComparing(GitLabEndpointUseCaseMethodCandidate::methodName))
                .map(method -> new FlowExplorerFlowMethod(
                        method.methodName(),
                        method.lineStart(),
                        method.lineEnd()
                ))
                .toList();
    }

    private List<FlowExplorerFlowRelation> relations(List<GitLabEndpointUseCaseRelation> relations) {
        return relations.stream()
                .map(relation -> new FlowExplorerFlowRelation(
                        relation.from(),
                        relation.to(),
                        relation.kind() != null ? relation.kind().name() : "UNKNOWN",
                        relation.reason(),
                        confidence(relation.confidence())
                ))
                .toList();
    }

    private FlowExplorerSnippetCardResult snippetCards(
            String gitLabGroup,
            String resolvedRef,
            List<FlowExplorerRepositoryContext> repositories,
            List<FlowExplorerFlowNode> flowNodes,
            FlowExplorerContextRequest request
    ) {
        var selectedRepository = repositories.stream()
                .filter(FlowExplorerRepositoryContext::selected)
                .findFirst()
                .orElse(null);
        return snippetCardService.buildSnippetCards(
                gitLabGroup,
                resolvedRef,
                selectedRepository,
                flowNodes,
                request.documentationPreset(),
                request.focusAreas()
        );
    }

    private FlowExplorerContextCoverage coverage(
            boolean endpointResolved,
            int repositoryRefCount,
            int attemptedRepositoryCount,
            List<FlowExplorerFlowNode> flowNodes,
            List<FlowExplorerFlowRelation> relations,
            FlowExplorerSnippetCardResult snippetCardResult,
            GitLabEndpointUseCaseContextResult selectedResult,
            List<String> limitations
    ) {
        var limits = selectedResult != null ? selectedResult.limits() : GitLabEndpointUseCaseLimits.defaults();
        var methodCount = flowNodes.stream().mapToInt(node -> node.methods().size()).sum();
        return new FlowExplorerContextCoverage(
                endpointResolved,
                repositoryRefCount,
                attemptedRepositoryCount,
                flowNodes.size(),
                methodCount,
                relations.size(),
                snippetCardResult.cards().size(),
                snippetCardResult.totalCharacterCount(),
                snippetCardResult.budgetReached(),
                selectedResult != null ? selectedResult.unresolved().size() : 0,
                distinct(limitations).size(),
                limits.maxDepthReached(),
                limits.maxFilesReached(),
                limits.readFileLimitReached(),
                selectedResult != null ? confidence(selectedResult.confidence()) : "LOW"
        );
    }

    private List<String> prefixedLimitations(String projectName, List<String> limitations) {
        return limitations.stream()
                .map(limitation -> projectName + ": " + limitation)
                .toList();
    }

    private List<String> distinct(List<String> values) {
        var distinctValues = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                distinctValues.add(value.trim());
            }
        }
        return List.copyOf(distinctValues);
    }

    private String confidence(GitLabEndpointUseCaseConfidence confidence) {
        return confidence != null ? confidence.name() : "LOW";
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    private record EndpointSelector(String projectName, String endpointId) {
    }
}
