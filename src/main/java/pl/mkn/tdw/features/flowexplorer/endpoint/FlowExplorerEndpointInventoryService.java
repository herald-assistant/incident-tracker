package pl.mkn.tdw.features.flowexplorer.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointOptionResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointParameterResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointSourceResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.EndpointTooltipDetailsResponse;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse.RepositoryInventoryResponse;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryScope;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryScopeRepository;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryScopeService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointDocumentation;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointParameterDocumentation;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FlowExplorerEndpointInventoryService {

    private final FlowExplorerRepositoryScopeService repositoryScopeService;
    private final GitLabRepositoryEndpointService gitLabRepositoryEndpointService;
    private final FlowExplorerEndpointInventoryCache endpointInventoryCache;

    public FlowExplorerEndpointInventoryResponse endpoints(
            String systemId,
            String branch,
            String endpointPathPrefix,
            String httpMethod
    ) {
        return endpoints(systemId, branch, endpointPathPrefix, httpMethod, false);
    }

    public FlowExplorerEndpointInventoryResponse endpoints(
            String systemId,
            String branch,
            String endpointPathPrefix,
            String httpMethod,
            boolean refreshCache
    ) {
        var scope = repositoryScopeService.resolve(systemId, branch);
        var normalizedEndpointPathPrefix = normalize(endpointPathPrefix);
        var normalizedHttpMethod = normalizeHttpMethod(httpMethod);
        var cacheKey = cacheKey(scope, normalizedEndpointPathPrefix, normalizedHttpMethod);
        if (refreshCache) {
            endpointInventoryCache.evict(cacheKey);
        } else {
            var cached = endpointInventoryCache.find(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        var repositoryResponses = new ArrayList<RepositoryInventoryResponse>();
        var endpointResponses = new ArrayList<EndpointOptionResponse>();
        var limitations = new ArrayList<>(scope.limitations());
        var candidateFileCount = 0;
        var scannedFileCount = 0;
        var scannedFileLimitReached = false;
        var scannedRepositoryCount = 0;
        var dataCollectedAt = (Instant) null;

        for (var repository : scope.repositories()) {
            try {
                var result = gitLabRepositoryEndpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                        scope.gitLabGroup(),
                        repository.projectName(),
                        scope.resolvedRef(),
                        normalizedEndpointPathPrefix,
                        normalizedHttpMethod,
                        repository.pathPrefixes(),
                        null,
                        refreshCache
                ));
                scannedRepositoryCount++;
                candidateFileCount += result.candidateFileCount();
                scannedFileCount += result.scannedFileCount();
                scannedFileLimitReached = scannedFileLimitReached || result.scannedFileLimitReached();
                dataCollectedAt = oldest(dataCollectedAt, result.dataCollectedAt());
                repositoryResponses.add(repositoryResponse(repository, scope.resolvedRef(), result));
                endpointResponses.addAll(result.endpoints().stream()
                        .map(endpoint -> endpointOption(repository, endpoint))
                        .toList());
                limitations.addAll(result.limitations().stream()
                        .map(limitation -> repository.repositoryId() + ": " + limitation)
                        .toList());
            } catch (RuntimeException exception) {
                var limitation = "Repository " + repository.repositoryId()
                        + " endpoint inventory failed: " + exception.getMessage();
                limitations.add(limitation);
                repositoryResponses.add(new RepositoryInventoryResponse(
                        repository.repositoryId(),
                        repository.projectName(),
                        repository.projectPath(),
                        scope.resolvedRef(),
                        repository.searchMode(),
                        repository.pathPrefixes(),
                        0,
                        0,
                        false,
                        0,
                        List.of(limitation)
                ));
            }
        }

        var sortedEndpoints = endpointResponses.stream()
                .sorted(Comparator.comparing(EndpointOptionResponse::path, Comparator.nullsLast(String::compareTo))
                        .thenComparing(EndpointOptionResponse::method, Comparator.nullsLast(String::compareTo))
                        .thenComparing(option -> option.source().projectName(), Comparator.nullsLast(String::compareTo)))
                .toList();

        var response = new FlowExplorerEndpointInventoryResponse(
                scope.system().id(),
                scope.requestedBranch(),
                scope.resolvedRef(),
                scope.gitLabGroup(),
                normalizedEndpointPathPrefix,
                normalizedHttpMethod,
                scope.repositoryRefCount(),
                scannedRepositoryCount,
                sortedEndpoints.size(),
                candidateFileCount,
                scannedFileCount,
                scannedFileLimitReached,
                dataCollectedAt != null ? dataCollectedAt : Instant.now(),
                repositoryResponses,
                sortedEndpoints,
                distinct(limitations)
        );
        endpointInventoryCache.save(cacheKey, response);
        return response;
    }

    private FlowExplorerEndpointInventoryCache.Key cacheKey(
            FlowExplorerRepositoryScope scope,
            String endpointPathPrefix,
            String httpMethod
    ) {
        var repositories = scope.repositories().stream()
                .map(repository -> String.join(":",
                        safe(repository.repositoryId()),
                        safe(repository.projectName()),
                        safe(repository.projectPath()),
                        safe(repository.searchMode()),
                        String.join(",", repository.pathPrefixes())
                ))
                .sorted()
                .toList();
        return new FlowExplorerEndpointInventoryCache.Key(
                scope.system().id(),
                scope.requestedBranch(),
                scope.resolvedRef(),
                scope.gitLabGroup(),
                repositories,
                endpointPathPrefix,
                httpMethod
        );
    }

    private Instant oldest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    private RepositoryInventoryResponse repositoryResponse(
            FlowExplorerRepositoryScopeRepository repository,
            String resolvedRef,
            GitLabRepositoryEndpointListResult result
    ) {
        return new RepositoryInventoryResponse(
                repository.repositoryId(),
                repository.projectName(),
                repository.projectPath(),
                resolvedRef,
                repository.searchMode(),
                repository.pathPrefixes(),
                result.candidateFileCount(),
                result.scannedFileCount(),
                result.scannedFileLimitReached(),
                result.endpoints().size(),
                result.limitations()
        );
    }

    private EndpointOptionResponse endpointOption(
            FlowExplorerRepositoryScopeRepository repository,
            GitLabRepositoryEndpoint endpoint
    ) {
        var documentation = endpoint.documentation();
        var parameters = parameters(documentation);
        var source = new EndpointSourceResponse(
                repository.repositoryId(),
                repository.projectName(),
                repository.projectPath(),
                endpoint.filePath(),
                endpoint.lineStart(),
                endpoint.lineEnd()
        );
        return new EndpointOptionResponse(
                uniqueEndpointId(repository.projectName(), endpoint),
                first(endpoint.httpMethods()),
                endpoint.httpMethods(),
                endpoint.path(),
                endpoint.pathExpression(),
                documentation != null ? documentation.summary() : null,
                documentation != null ? firstDefined(documentation.description(), documentation.summary()) : null,
                documentation != null ? documentation.operationId() : null,
                documentation != null ? documentation.tags() : List.of(),
                endpoint.controllerClass(),
                endpoint.handlerMethod(),
                source,
                parameters,
                endpoint.confidence(),
                endpoint.limitations(),
                endpoint.suggestedNextReads(),
                tooltipDetails(endpoint, documentation, parameters)
        );
    }

    private EndpointTooltipDetailsResponse tooltipDetails(
            GitLabRepositoryEndpoint endpoint,
            GitLabRepositoryEndpointDocumentation documentation,
            List<EndpointParameterResponse> parameters
    ) {
        return new EndpointTooltipDetailsResponse(
                documentation != null ? documentation.source() : null,
                documentation != null ? documentation.summary() : null,
                documentation != null ? documentation.description() : null,
                documentation != null ? documentation.operationId() : null,
                documentation != null ? documentation.tags() : List.of(),
                parameters,
                endpoint.requestTypes(),
                endpoint.responseTypes(),
                endpoint.annotations(),
                endpoint.limitations(),
                endpoint.suggestedNextReads()
        );
    }

    private List<EndpointParameterResponse> parameters(GitLabRepositoryEndpointDocumentation documentation) {
        if (documentation == null) {
            return List.of();
        }
        return documentation.parameters().stream()
                .map(this::parameter)
                .toList();
    }

    private EndpointParameterResponse parameter(GitLabRepositoryEndpointParameterDocumentation parameter) {
        return new EndpointParameterResponse(
                parameter.name(),
                parameter.in(),
                parameter.required(),
                parameter.type(),
                parameter.description()
        );
    }

    private String uniqueEndpointId(String projectName, GitLabRepositoryEndpoint endpoint) {
        var endpointId = firstDefined(endpoint.endpointId(), endpoint.path());
        if (!StringUtils.hasText(endpointId)) {
            endpointId = firstDefined(endpoint.controllerClass(), endpoint.handlerMethod());
        }
        return projectName + ":" + endpointId;
    }

    private String normalizeHttpMethod(String httpMethod) {
        var normalized = normalize(httpMethod);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
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

    private String first(List<String> values) {
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    private String firstDefined(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
