package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryAnalysisCache;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpoint;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GitLabEndpointUseCaseContextService {

    private final GitLabEndpointUseCaseSourceSnapshotService sourceSnapshotService;
    private final GitLabEndpointUseCaseCodeIndexService codeIndexService;
    private final GitLabEndpointUseCaseEndpointIndexService endpointIndexService;
    private final GitLabEndpointUseCaseSpringBeanRegistryService springBeanRegistryService;
    private final GitLabEndpointUseCaseDependencyInjectionResolver dependencyInjectionResolver;
    private final GitLabEndpointUseCaseCallTargetResolver callTargetResolver;
    private final GitLabEndpointUseCaseGraphBuilderService graphBuilderService;
    private final GitLabEndpointUseCaseContextCompressorService contextCompressorService;
    private final GitLabRepositoryEndpointService repositoryEndpointService;
    private final GitLabRepositoryAnalysisCache analysisCache;

    @Autowired
    GitLabEndpointUseCaseContextService(
            GitLabEndpointUseCaseSourceSnapshotService sourceSnapshotService,
            GitLabEndpointUseCaseCodeIndexService codeIndexService,
            GitLabEndpointUseCaseEndpointIndexService endpointIndexService,
            GitLabEndpointUseCaseSpringBeanRegistryService springBeanRegistryService,
            GitLabEndpointUseCaseDependencyInjectionResolver dependencyInjectionResolver,
            GitLabEndpointUseCaseCallTargetResolver callTargetResolver,
            GitLabEndpointUseCaseGraphBuilderService graphBuilderService,
            GitLabEndpointUseCaseContextCompressorService contextCompressorService,
            GitLabRepositoryEndpointService repositoryEndpointService,
            GitLabRepositoryAnalysisCache analysisCache
    ) {
        this.sourceSnapshotService = sourceSnapshotService;
        this.codeIndexService = codeIndexService;
        this.endpointIndexService = endpointIndexService;
        this.springBeanRegistryService = springBeanRegistryService;
        this.dependencyInjectionResolver = dependencyInjectionResolver;
        this.callTargetResolver = callTargetResolver;
        this.graphBuilderService = graphBuilderService;
        this.contextCompressorService = contextCompressorService;
        this.repositoryEndpointService = repositoryEndpointService;
        this.analysisCache = analysisCache;
    }

    GitLabEndpointUseCaseContextService(
            GitLabEndpointUseCaseSourceSnapshotService sourceSnapshotService,
            GitLabEndpointUseCaseCodeIndexService codeIndexService,
            GitLabEndpointUseCaseEndpointIndexService endpointIndexService,
            GitLabEndpointUseCaseSpringBeanRegistryService springBeanRegistryService,
            GitLabEndpointUseCaseDependencyInjectionResolver dependencyInjectionResolver,
            GitLabEndpointUseCaseCallTargetResolver callTargetResolver,
            GitLabEndpointUseCaseGraphBuilderService graphBuilderService,
            GitLabEndpointUseCaseContextCompressorService contextCompressorService,
            GitLabRepositoryEndpointService repositoryEndpointService
    ) {
        this(
                sourceSnapshotService,
                codeIndexService,
                endpointIndexService,
                springBeanRegistryService,
                dependencyInjectionResolver,
                callTargetResolver,
                graphBuilderService,
                contextCompressorService,
                repositoryEndpointService,
                null
        );
    }

    public GitLabEndpointUseCaseContextResult buildContext(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var effectiveRequest = request != null
                ? request
                : new GitLabEndpointUseCaseContextRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        var sourceSnapshot = sourceSnapshotService.buildSnapshot(group, branch, effectiveRequest);
        var indexWorkspace = indexWorkspace(sourceSnapshot);
        var codeIndex = indexWorkspace.codeIndex();
        var endpointIndex = indexWorkspace.endpointIndex();
        var endpointMatch = endpointIndexService.match(effectiveRequest, endpointIndex);
        if (!endpointMatch.matched()) {
            endpointMatch = fallbackEndpointMatchFromRepositoryInventory(
                    group,
                    branch,
                    effectiveRequest,
                    codeIndex,
                    endpointMatch
            );
        }
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>();
        warnings.addAll(sourceSnapshot.warnings());
        warnings.addAll(codeIndex.warnings());
        warnings.addAll(endpointIndex.warnings());
        warnings.addAll(endpointMatch.warnings());

        var repository = repositoryContext(sourceSnapshot, codeIndex);
        if (!endpointMatch.matched()) {
            var emptyGraphBuild = new GitLabEndpointUseCaseGraphBuildResult(
                    GitLabEndpointUseCaseGraph.empty(),
                    new GitLabEndpointUseCaseLimits(
                            effectiveRequest.maxDepth(),
                            effectiveRequest.maxNodes(),
                            false,
                            false
                    ),
                    deduplicateWarnings(warnings)
            );
            var compressed = contextCompressorService.compress(null, emptyGraphBuild, effectiveRequest.outputMode());
            return result(repository, null, compressed, emptyGraphBuild, deduplicateWarnings(warnings));
        }

        var dependencyWorkspace = dependencyWorkspace(codeIndex);
        var springBeanRegistry = dependencyWorkspace.springBeanRegistry();
        var dependencyResolution = dependencyWorkspace.dependencyResolution();
        var callTargetResolution = dependencyWorkspace.callTargetResolution();
        var graphBuild = graphBuilderService.buildGraph(
                endpointMatch.endpoint(),
                codeIndex,
                springBeanRegistry,
                callTargetResolution,
                effectiveRequest.maxDepth(),
                effectiveRequest.maxNodes()
        );
        warnings.addAll(springBeanRegistry.warnings());
        warnings.addAll(dependencyResolution.warnings());
        warnings.addAll(callTargetResolution.warnings());
        warnings.addAll(graphBuild.warnings());

        var deduplicatedWarnings = deduplicateWarnings(warnings);
        var graphBuildWithWarnings = new GitLabEndpointUseCaseGraphBuildResult(
                graphBuild.graph(),
                graphBuild.limits(),
                deduplicatedWarnings
        );
        var compressed = contextCompressorService.compress(
                endpointMatch.endpoint(),
                graphBuildWithWarnings,
                effectiveRequest.outputMode()
        );

        log.info(
                "Built GitLab endpoint use case context group={} projectName={} branch={} endpointMatched={} nodeCount={} edgeCount={} classCount={} warningCount={} confidence={}",
                repository.group(),
                repository.projectName(),
                repository.requestedBranch(),
                true,
                compressed.graph().nodes().size(),
                compressed.graph().edges().size(),
                compressed.classList().size(),
                deduplicatedWarnings.size(),
                compressed.confidence()
        );

        return result(
                repository,
                endpointContext(endpointMatch.endpoint(), effectiveRequest),
                compressed,
                graphBuildWithWarnings,
                deduplicatedWarnings
        );
    }

    private GitLabEndpointUseCaseContextResult result(
            GitLabEndpointUseCaseRepositoryContext repository,
            GitLabEndpointUseCaseEndpointContext endpoint,
            GitLabEndpointUseCaseCompressedContext compressed,
            GitLabEndpointUseCaseGraphBuildResult graphBuild,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        return new GitLabEndpointUseCaseContextResult(
                repository,
                endpoint,
                compressed.useCaseSummary(),
                compressed.graph(),
                compressed.classList(),
                warnings,
                compressed.evidence(),
                compressed.suggestedNextReads(),
                graphBuild.limits(),
                compressed.confidence()
        );
    }

    private GitLabEndpointUseCaseIndexWorkspace indexWorkspace(GitLabEndpointUseCaseSourceSnapshot sourceSnapshot) {
        if (analysisCache == null || sourceSnapshot == null) {
            return buildIndexWorkspace(sourceSnapshot);
        }

        return analysisCache.getOrCompute(
                "gitlab.endpoint-usecase.index-workspace",
                List.of(sourceSnapshotFingerprint(sourceSnapshot)),
                () -> buildIndexWorkspace(sourceSnapshot)
        );
    }

    private GitLabEndpointUseCaseIndexWorkspace buildIndexWorkspace(GitLabEndpointUseCaseSourceSnapshot sourceSnapshot) {
        var codeIndex = codeIndexService.buildIndex(sourceSnapshot);
        var endpointIndex = endpointIndexService.buildIndex(codeIndex);
        return new GitLabEndpointUseCaseIndexWorkspace(codeIndex, endpointIndex);
    }

    private GitLabEndpointUseCaseDependencyWorkspace dependencyWorkspace(GitLabEndpointUseCaseCodeIndex codeIndex) {
        if (analysisCache == null || codeIndex == null) {
            return buildDependencyWorkspace(codeIndex);
        }

        return analysisCache.getOrCompute(
                "gitlab.endpoint-usecase.dependency-workspace",
                List.of(codeIndexFingerprint(codeIndex)),
                () -> buildDependencyWorkspace(codeIndex)
        );
    }

    private GitLabEndpointUseCaseDependencyWorkspace buildDependencyWorkspace(GitLabEndpointUseCaseCodeIndex codeIndex) {
        var springBeanRegistry = springBeanRegistryService.buildRegistry(codeIndex);
        var dependencyResolution = dependencyInjectionResolver.resolve(codeIndex, springBeanRegistry);
        var callTargetResolution = callTargetResolver.resolve(codeIndex, springBeanRegistry, dependencyResolution);
        return new GitLabEndpointUseCaseDependencyWorkspace(
                springBeanRegistry,
                dependencyResolution,
                callTargetResolution
        );
    }

    private String sourceSnapshotFingerprint(GitLabEndpointUseCaseSourceSnapshot snapshot) {
        var fingerprint = new StringBuilder();
        fingerprint.append(safe(snapshot.group())).append('|')
                .append(safe(snapshot.projectName())).append('|')
                .append(safe(snapshot.branch())).append('|')
                .append(safe(snapshot.sourcePathPrefix())).append('|')
                .append(snapshot.maxSourceFiles()).append('|')
                .append(snapshot.maxFileCharacters()).append('|')
                .append(snapshot.indexStatus());

        for (var file : snapshot.files()) {
            fingerprint.append('|')
                    .append(safe(file.path()))
                    .append(':')
                    .append(file.characterCount())
                    .append(':')
                    .append(file.truncated())
                    .append(':')
                    .append(file.content() != null ? file.content().hashCode() : 0);
        }
        return fingerprint.toString();
    }

    private String codeIndexFingerprint(GitLabEndpointUseCaseCodeIndex codeIndex) {
        var snapshot = codeIndex.sourceSnapshot();
        return sourceSnapshotFingerprint(snapshot)
                + "|types=" + codeIndex.types().size()
                + "|calls=" + codeIndex.methodCallIndex().calls().size()
                + "|status=" + codeIndex.indexStatus()
                + "|warnings=" + Objects.hash(codeIndex.warnings());
    }

    private GitLabEndpointUseCaseRepositoryContext repositoryContext(
            GitLabEndpointUseCaseSourceSnapshot snapshot,
            GitLabEndpointUseCaseCodeIndex codeIndex
    ) {
        return new GitLabEndpointUseCaseRepositoryContext(
                snapshot.group(),
                snapshot.projectName(),
                snapshot.branch(),
                snapshot.sourcePathPrefix(),
                codeIndex != null ? codeIndex.indexStatus() : snapshot.indexStatus()
        );
    }

    private GitLabEndpointUseCaseEndpointContext endpointContext(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseContextRequest request
    ) {
        return new GitLabEndpointUseCaseEndpointContext(
                endpoint.endpointId(),
                endpoint.httpMethods(),
                StringUtils.hasText(request.endpointPath()) ? request.endpointPath() : endpoint.pathPattern(),
                endpoint.pathPattern(),
                endpoint.controllerClass(),
                endpoint.controllerMethod(),
                endpoint.sourcePath(),
                endpoint.lineStart() != null ? endpoint.lineStart() : -1,
                endpoint.lineEnd() != null ? endpoint.lineEnd() : -1
        );
    }

    private GitLabEndpointUseCaseEndpointMatchResult fallbackEndpointMatchFromRepositoryInventory(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseEndpointMatchResult primaryMatch
    ) {
        if (repositoryEndpointService == null
                || codeIndex == null
                || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT
                || !hasEndpointMatchInput(request)) {
            return primaryMatch;
        }

        try {
            var repositoryEndpointResult = repositoryEndpointService.listEndpoints(
                    new GitLabRepositoryEndpointListRequest(
                            group,
                            request.projectName(),
                            branch,
                            null,
                            request.httpMethod(),
                            request.sourcePathPrefix(),
                            null
                    )
            );
            var candidates = repositoryEndpointResult.endpoints().stream()
                    .filter(endpoint -> matchesRequest(endpoint, request))
                    .map(endpoint -> endpointCandidate(endpoint, codeIndex))
                    .flatMap(java.util.Optional::stream)
                    .sorted(Comparator.comparing(
                                    GitLabEndpointUseCaseEndpointCandidate::pathPattern,
                                    Comparator.nullsLast(String::compareTo))
                            .thenComparing(GitLabEndpointUseCaseEndpointCandidate::controllerClass)
                            .thenComparing(GitLabEndpointUseCaseEndpointCandidate::controllerMethod))
                    .toList();

            if (candidates.size() == 1) {
                return new GitLabEndpointUseCaseEndpointMatchResult(candidates.get(0), candidates, List.of());
            }
            if (candidates.size() > 1) {
                return new GitLabEndpointUseCaseEndpointMatchResult(
                        null,
                        candidates,
                        List.of(new GitLabEndpointUseCaseWarning(
                                GitLabEndpointUseCaseWarningCodes.ENDPOINT_AMBIGUOUS,
                                GitLabEndpointUseCaseWarningSeverity.WARNING,
                                "Endpoint match is ambiguous after repository endpoint inventory fallback; narrow endpointId or httpMethod + endpointPath.",
                                null,
                                null,
                                endpointIds(candidates)
                        ))
                );
            }
            return primaryMatch;
        } catch (RuntimeException exception) {
            var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(primaryMatch.warnings());
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.REPOSITORY_ENDPOINT_INVENTORY_FALLBACK_FAILED,
                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                    "Repository endpoint inventory fallback failed while matching OpenAPI-backed endpoint: "
                            + safeMessage(exception),
                    null,
                    null,
                    List.of()
            ));
            return new GitLabEndpointUseCaseEndpointMatchResult(primaryMatch.endpoint(), primaryMatch.candidates(), warnings);
        }
    }

    private java.util.Optional<GitLabEndpointUseCaseEndpointCandidate> endpointCandidate(
            GitLabRepositoryEndpoint endpoint,
            GitLabEndpointUseCaseCodeIndex codeIndex
    ) {
        if (endpoint == null
                || !StringUtils.hasText(endpoint.controllerClass())
                || !StringUtils.hasText(endpoint.handlerMethod())) {
            return java.util.Optional.empty();
        }

        return codeIndex.findType(endpoint.controllerClass())
                .flatMap(type -> method(type, endpoint)
                        .map(method -> new GitLabEndpointUseCaseEndpointCandidate(
                                endpoint.endpointId(),
                                endpoint.httpMethods(),
                                endpoint.path(),
                                endpoint.pathExpression(),
                                endpoint.controllerClass(),
                                endpoint.handlerMethod(),
                                method.id(),
                                endpoint.filePath(),
                                positiveLine(endpoint.lineStart()),
                                positiveLine(endpoint.lineEnd()),
                                endpoint.requestTypes(),
                                endpoint.responseTypes(),
                                endpoint.annotations(),
                                confidence(endpoint.confidence()),
                                endpoint.limitations()
                        )));
    }

    private java.util.Optional<GitLabEndpointUseCaseMethodInfo> method(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabRepositoryEndpoint endpoint
    ) {
        var methodNameMatches = type.methods().stream()
                .filter(method -> !method.constructor())
                .filter(method -> endpoint.handlerMethod().equals(method.name()))
                .toList();
        if (methodNameMatches.size() <= 1) {
            return methodNameMatches.stream().findFirst();
        }

        var lineAwareMatches = methodNameMatches.stream()
                .filter(method -> overlaps(method.lineStart(), method.lineEnd(), endpoint.lineStart(), endpoint.lineEnd()))
                .toList();
        if (lineAwareMatches.size() == 1) {
            return java.util.Optional.of(lineAwareMatches.get(0));
        }
        return methodNameMatches.stream().findFirst();
    }

    private boolean matchesRequest(GitLabRepositoryEndpoint endpoint, GitLabEndpointUseCaseContextRequest request) {
        if (endpoint == null || request == null) {
            return false;
        }
        if (StringUtils.hasText(request.endpointId())) {
            return request.endpointId().trim().equals(endpoint.endpointId());
        }

        var requestedHttpMethod = normalizeHttpMethod(request.httpMethod());
        var requestedPath = normalizeHttpPath(request.endpointPath());
        return StringUtils.hasText(requestedHttpMethod)
                && StringUtils.hasText(requestedPath)
                && matchesHttpMethod(endpoint, requestedHttpMethod)
                && matchesPath(endpoint.path(), requestedPath);
    }

    private boolean matchesHttpMethod(GitLabRepositoryEndpoint endpoint, String httpMethod) {
        return endpoint.httpMethods().contains(httpMethod) || endpoint.httpMethods().contains("ANY");
    }

    private boolean matchesPath(String pathPattern, String requestedPath) {
        var normalizedPattern = normalizeHttpPath(pathPattern);
        var normalizedRequestedPath = normalizeHttpPath(requestedPath);
        if (!StringUtils.hasText(normalizedPattern) || !StringUtils.hasText(normalizedRequestedPath)) {
            return false;
        }
        if (normalizedPattern.equals(normalizedRequestedPath)) {
            return true;
        }
        return Pattern.compile(pathPatternRegex(normalizedPattern))
                .matcher(normalizedRequestedPath)
                .matches();
    }

    private String pathPatternRegex(String pathPattern) {
        if ("/".equals(pathPattern)) {
            return "^/$";
        }
        var regex = new StringBuilder("^");
        for (var segment : pathPattern.substring(1).split("/")) {
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                regex.append("[^/]+");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private boolean hasEndpointMatchInput(GitLabEndpointUseCaseContextRequest request) {
        return request != null
                && (StringUtils.hasText(request.endpointId())
                || (StringUtils.hasText(request.httpMethod()) && StringUtils.hasText(request.endpointPath())));
    }

    private String normalizeHttpMethod(String httpMethod) {
        return StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeHttpPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean overlaps(Integer methodLineStart, Integer methodLineEnd, int endpointLineStart, int endpointLineEnd) {
        if (methodLineStart == null || endpointLineStart <= 0) {
            return true;
        }
        var methodEnd = methodLineEnd != null ? methodLineEnd : methodLineStart;
        var endpointEnd = endpointLineEnd > 0 ? endpointLineEnd : endpointLineStart;
        return methodLineStart <= endpointEnd && endpointLineStart <= methodEnd;
    }

    private Integer positiveLine(int line) {
        return line > 0 ? line : null;
    }

    private GitLabEndpointUseCaseConfidence confidence(String value) {
        if (!StringUtils.hasText(value)) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> GitLabEndpointUseCaseConfidence.HIGH;
            case "LOW" -> GitLabEndpointUseCaseConfidence.LOW;
            default -> GitLabEndpointUseCaseConfidence.MEDIUM;
        };
    }

    private List<String> endpointIds(List<GitLabEndpointUseCaseEndpointCandidate> endpoints) {
        var ids = new LinkedHashSet<String>();
        for (var endpoint : endpoints != null ? endpoints : List.<GitLabEndpointUseCaseEndpointCandidate>of()) {
            if (StringUtils.hasText(endpoint.endpointId())) {
                ids.add(endpoint.endpointId());
            }
        }
        return ids.stream().limit(20).toList();
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private List<GitLabEndpointUseCaseWarning> deduplicateWarnings(List<GitLabEndpointUseCaseWarning> warnings) {
        var byKey = new LinkedHashMap<String, GitLabEndpointUseCaseWarning>();
        for (var warning : warnings != null ? warnings : List.<GitLabEndpointUseCaseWarning>of()) {
            if (warning == null) {
                continue;
            }
            byKey.putIfAbsent(warningKey(warning), warning);
        }
        return List.copyOf(byKey.values());
    }

    private String warningKey(GitLabEndpointUseCaseWarning warning) {
        return String.join("|",
                safe(warning.code()),
                safe(warning.message()),
                safe(warning.sourcePath()),
                warning.line() != null ? warning.line().toString() : ""
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private record GitLabEndpointUseCaseIndexWorkspace(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseEndpointIndex endpointIndex
    ) {
    }

    private record GitLabEndpointUseCaseDependencyWorkspace(
            GitLabEndpointUseCaseSpringBeanRegistry springBeanRegistry,
            GitLabEndpointUseCaseDependencyResolution dependencyResolution,
            GitLabEndpointUseCaseCallTargetResolution callTargetResolution
    ) {
    }
}
