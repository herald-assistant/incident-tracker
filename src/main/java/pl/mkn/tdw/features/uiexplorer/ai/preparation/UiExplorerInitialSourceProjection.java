package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependency;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.util.LinkedHashSet;
import java.util.Set;

final class UiExplorerInitialSourceProjection {

    static final int INITIAL_COMPONENT_DEPTH = 1;

    private final Set<String> componentIds;
    private final Set<String> dependencyIds;
    private final int totalComponentCount;
    private final int totalDependencyCount;
    private final int targetableDependencyCount;

    private UiExplorerInitialSourceProjection(
            Set<String> componentIds,
            Set<String> dependencyIds,
            int totalComponentCount,
            int totalDependencyCount,
            int targetableDependencyCount
    ) {
        this.componentIds = Set.copyOf(componentIds);
        this.dependencyIds = Set.copyOf(dependencyIds);
        this.totalComponentCount = totalComponentCount;
        this.totalDependencyCount = totalDependencyCount;
        this.targetableDependencyCount = targetableDependencyCount;
    }

    static UiExplorerInitialSourceProjection from(GitLabFrontendScreenReachabilityGraph graph) {
        var components = graph.componentLevels().stream()
                .flatMap(level -> level.components().stream())
                .toList();
        var componentIds = components.stream()
                .filter(component -> component.depth() <= INITIAL_COMPONENT_DEPTH)
                .map(GitLabFrontendReachabilityComponent::componentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var dependencyIds = graph.dependencies().stream()
                .filter(dependency -> hasText(dependency.sourcePath()) && hasText(dependency.sliceContent()))
                .filter(dependency -> dependency.usedBy().stream().anyMatch(componentIds::contains))
                .map(GitLabFrontendReachabilityDependency::dependencyId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new UiExplorerInitialSourceProjection(
                componentIds,
                dependencyIds,
                components.size(),
                graph.dependencies().size(),
                Math.toIntExact(graph.dependencies().stream()
                        .filter(dependency -> hasText(dependency.sourcePath()))
                        .count())
        );
    }

    boolean embeds(GitLabFrontendReachabilityComponent component) {
        return componentIds.contains(component.componentId());
    }

    boolean embeds(GitLabFrontendReachabilityDependency dependency) {
        return dependencyIds.contains(dependency.dependencyId());
    }

    int embeddedComponentCount() {
        return componentIds.size();
    }

    int deferredComponentCount() {
        return Math.max(0, totalComponentCount - componentIds.size());
    }

    int embeddedDependencyCount() {
        return dependencyIds.size();
    }

    int deferredDependencyCount() {
        return Math.max(0, targetableDependencyCount - dependencyIds.size());
    }

    int unavailableDependencyCount() {
        return Math.max(0, totalDependencyCount - targetableDependencyCount);
    }

    int embeddedTargetCount(GitLabFrontendScreenReachabilityGraph graph) {
        var templates = graph.componentLevels().stream()
                .flatMap(level -> level.components().stream())
                .filter(this::embeds)
                .filter(component -> hasText(component.templateContent()))
                .count();
        return embeddedComponentCount() + embeddedDependencyCount() + Math.toIntExact(templates);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
