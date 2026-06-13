package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
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
        var codeIndex = codeIndexService.buildIndex(sourceSnapshot);
        var endpointIndex = endpointIndexService.buildIndex(codeIndex);
        var endpointMatch = endpointIndexService.match(effectiveRequest, endpointIndex);
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

        var springBeanRegistry = springBeanRegistryService.buildRegistry(codeIndex);
        var dependencyResolution = dependencyInjectionResolver.resolve(codeIndex, springBeanRegistry);
        var callTargetResolution = callTargetResolver.resolve(codeIndex, springBeanRegistry, dependencyResolution);
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
}
