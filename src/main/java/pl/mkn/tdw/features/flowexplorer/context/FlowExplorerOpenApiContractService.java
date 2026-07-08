package pl.mkn.tdw.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FlowExplorerOpenApiContractService {

    private static final int MAX_OPENAPI_CONTRACTS = 3;
    private static final int OPENAPI_SCHEMA_DEPTH = 2;
    private static final int OPENAPI_MAX_CHARACTERS = 20_000;
    private static final String OPENAPI_CONTRACT_ROLE = "OPENAPI_CONTRACT";

    private final GitLabOpenApiEndpointSliceService openApiEndpointSliceService;

    public FlowExplorerOpenApiContractResult buildEndpointContracts(
            String gitLabGroup,
            String resolvedRef,
            FlowExplorerRepositoryContext repository,
            List<FlowExplorerFlowNode> flowNodes,
            FlowExplorerContextRequest request
    ) {
        if (repository == null || !StringUtils.hasText(repository.projectName())) {
            return FlowExplorerOpenApiContractResult.empty();
        }
        if (request == null || !StringUtils.hasText(request.httpMethod()) || !StringUtils.hasText(request.endpointPath())) {
            return FlowExplorerOpenApiContractResult.empty();
        }

        var candidates = openApiCandidates(flowNodes);
        if (candidates.isEmpty()) {
            return FlowExplorerOpenApiContractResult.empty();
        }

        var contracts = new ArrayList<FlowExplorerOpenApiEndpointContract>();
        var limitations = new ArrayList<String>();
        for (var candidate : candidates.stream().limit(MAX_OPENAPI_CONTRACTS).toList()) {
            var boundaryLimitations = focusedReadBoundaryLimitations(repository, candidate.filePath());
            try {
                var response = openApiEndpointSliceService.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                        gitLabGroup,
                        repository.projectName(),
                        resolvedRef,
                        candidate.filePath(),
                        request.httpMethod(),
                        request.endpointPath(),
                        true,
                        OPENAPI_SCHEMA_DEPTH,
                        OPENAPI_MAX_CHARACTERS
                ));
                limitations.addAll(response.limitations().stream()
                        .map(limitation -> candidate.filePath() + ": " + limitation)
                        .toList());
                limitations.addAll(boundaryLimitations.stream()
                        .map(limitation -> candidate.filePath() + ": " + limitation)
                        .toList());
                if (GitLabOpenApiEndpointSliceService.STATUS_OK.equals(response.status())) {
                    contracts.add(contract(response, boundaryLimitations));
                }
            } catch (RuntimeException exception) {
                limitations.add(candidate.filePath() + ": OpenAPI endpoint slice failed: " + safeMessage(exception));
            }
        }

        if (candidates.size() > MAX_OPENAPI_CONTRACTS) {
            limitations.add("OpenAPI endpoint contract candidates were truncated to maxContracts="
                    + MAX_OPENAPI_CONTRACTS + ".");
        }

        return new FlowExplorerOpenApiContractResult(contracts, distinct(limitations));
    }

    private List<FlowExplorerFlowNode> openApiCandidates(List<FlowExplorerFlowNode> flowNodes) {
        var seen = new LinkedHashSet<String>();
        var candidates = new ArrayList<FlowExplorerFlowNode>();
        for (var node : flowNodes != null ? flowNodes : List.<FlowExplorerFlowNode>of()) {
            if (node == null || !StringUtils.hasText(node.filePath()) || !openApiCandidate(node)) {
                continue;
            }
            if (seen.add(node.filePath().trim())) {
                candidates.add(node);
            }
        }
        return List.copyOf(candidates);
    }

    private boolean openApiCandidate(FlowExplorerFlowNode node) {
        var role = StringUtils.hasText(node.role()) ? node.role().trim().toUpperCase(Locale.ROOT) : "";
        if (OPENAPI_CONTRACT_ROLE.equals(role)) {
            return true;
        }
        var path = node.filePath().trim().toLowerCase(Locale.ROOT);
        return (path.endsWith(".yaml") || path.endsWith(".yml"))
                && (path.contains("openapi") || path.contains("swagger") || path.contains("/api/"));
    }

    private boolean pathWithinBoundary(FlowExplorerRepositoryContext repository, String filePath) {
        if (repository == null || repository.pathPrefixes().isEmpty()) {
            return true;
        }
        var normalizedPath = normalizeFilePath(filePath);
        if (!StringUtils.hasText(normalizedPath)) {
            return false;
        }
        return repository.pathPrefixes().stream()
                .map(this::normalizeFilePath)
                .filter(StringUtils::hasText)
                .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    private List<String> focusedReadBoundaryLimitations(FlowExplorerRepositoryContext repository, String filePath) {
        if (pathWithinBoundary(repository, filePath)) {
            return List.of();
        }
        return List.of("File is outside default repository discovery scope and was read because it was explicitly requested.");
    }

    private String normalizeFilePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private FlowExplorerOpenApiEndpointContract contract(
            GitLabOpenApiEndpointSliceResponse response,
            List<String> extraLimitations
    ) {
        var limitations = new ArrayList<String>(response.limitations());
        limitations.addAll(extraLimitations != null ? extraLimitations : List.of());
        return new FlowExplorerOpenApiEndpointContract(
                response.projectName(),
                response.filePath(),
                response.httpMethod(),
                response.endpointPath(),
                response.matchedPath(),
                response.operationId(),
                response.summary(),
                response.description(),
                response.tags(),
                response.sourceRef(),
                response.content(),
                response.returnedCharacters(),
                response.truncated(),
                distinct(limitations)
        );
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

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
