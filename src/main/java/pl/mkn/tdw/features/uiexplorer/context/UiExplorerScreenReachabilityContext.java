package pl.mkn.tdw.features.uiexplorer.context;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.util.LinkedHashSet;
import java.util.List;

public record UiExplorerScreenReachabilityContext(
        String systemId,
        String systemLabel,
        UiExplorerSourceScope sourceScope,
        UiExplorerScreenIdentity screen,
        String screenDiscoveryStatus,
        boolean lazyLoaded,
        List<String> guards,
        List<String> routeParameters,
        List<String> screenLimitations,
        UiExplorerSourceReference routeSource,
        UiExplorerSourceRevision sourceRevision,
        UiExplorerCoverageStatus status,
        GitLabFrontendScreenReachabilityGraph graph,
        List<UiExplorerSectionContextCoverage> sectionCoverage,
        UiExplorerReachabilityBoundary boundary,
        List<String> researchGaps,
        List<String> visibilityLimits
) {
    public UiExplorerScreenReachabilityContext {
        guards = guards != null ? List.copyOf(guards) : List.of();
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        screenLimitations = screenLimitations != null ? List.copyOf(screenLimitations) : List.of();
        sectionCoverage = sectionCoverage != null ? List.copyOf(sectionCoverage) : List.of();
        researchGaps = researchGaps != null ? List.copyOf(researchGaps) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public List<GitLabFrontendReachabilityComponent> components() {
        return graph != null
                ? graph.componentLevels().stream().flatMap(level -> level.components().stream()).toList()
                : List.of();
    }

    public List<String> sourcePaths() {
        var paths = new LinkedHashSet<String>();
        if (graph != null) {
            graph.effectiveRouteChain().segments().stream()
                    .map(segment -> segment.source().path())
                    .filter(java.util.Objects::nonNull)
                    .forEach(paths::add);
            components().forEach(component -> {
                if (component.sourcePath() != null) paths.add(component.sourcePath());
                if (component.templatePath() != null) paths.add(component.templatePath());
            });
            graph.dependencies().stream().map(dependency -> dependency.sourcePath())
                    .filter(java.util.Objects::nonNull).forEach(paths::add);
        }
        return List.copyOf(paths);
    }

}
