package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependency;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyCategory;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolCandidate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class UiExplorerReachabilityOutlineRenderer {

    String render(
            GitLabFrontendScreenReachabilityGraph graph,
            UiExplorerInitialSourceProjection projection
    ) {
        var lines = new ArrayList<String>();
        var routeOutline = routeOutline(graph.readableOutline());
        if (StringUtils.hasText(routeOutline)) {
            lines.add(routeOutline);
            lines.add("");
        }

        var components = graph.componentLevels().stream()
                .flatMap(level -> level.components().stream())
                .toList();
        var componentReferences = new LinkedHashMap<String, String>();
        components.forEach(component -> componentReferences.put(
                component.componentId(), "C" + (component.breadthFirstOrder() + 1)
        ));
        var materialDependencies = graph.dependencies().stream()
                .filter(this::materialDependency)
                .toList();
        var supportingTargetableDependencies = graph.dependencies().stream()
                .filter(dependency -> !materialDependency(dependency))
                .filter(dependency -> StringUtils.hasText(dependency.sourcePath()))
                .toList();
        var dependencyReferences = new LinkedHashMap<String, String>();
        graph.dependencies().forEach(dependency -> dependencyReferences.put(
                dependency.dependencyId(), "D" + (dependency.discoveryOrder() + 1)
        ));

        lines.add("## Targetable component BFS frontier");
        lines.add("");
        lines.add("`sliceRef` values are session-bound targets. `EMBEDDED` source is present in the initial source artifact; `ON_DEMAND` source must be read with the narrow frontend slice tool when an active section needs it.");
        for (var level : graph.componentLevels()) {
            lines.add("");
            lines.add("### Depth " + level.depth());
            for (var component : level.components()) {
                lines.add(componentLine(component, projection, componentReferences, dependencyReferences));
            }
        }

        lines.add("");
        lines.add("## Targetable functional dependency frontier");
        lines.add("");
        if (materialDependencies.isEmpty()) {
            lines.add("- none");
        } else {
            materialDependencies.forEach(dependency -> lines.add(dependencyLine(
                    dependency, projection, dependencyReferences
            )));
        }

        if (!supportingTargetableDependencies.isEmpty()) {
            lines.add("");
            lines.add("## Targetable supporting dependency frontier");
            lines.add("");
            lines.add("Supporting targets stay compact, but retain their exact `sliceRef` for a narrow read when a functional claim needs their source.");
            supportingTargetableDependencies.forEach(dependency -> lines.add(supportingDependencyLine(
                    dependency, projection, dependencyReferences
            )));
        }

        var technicalSummary = graph.dependencies().stream()
                .filter(dependency -> !materialDependency(dependency))
                .filter(dependency -> !StringUtils.hasText(dependency.sourcePath()))
                .collect(Collectors.groupingBy(
                        dependency -> dependency.category() + "/" + dependency.kind(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        if (!technicalSummary.isEmpty()) {
            lines.add("");
            lines.add("## Non-functional dependency summary");
            technicalSummary.forEach((kind, count) -> lines.add("- " + kind + ": " + count));
        }

        if (!graph.limitations().isEmpty()) {
            lines.add("");
            lines.add("## Explicit boundaries");
            graph.limitations().forEach(limitation -> lines.add("- " + safe(limitation)));
        }
        return String.join(System.lineSeparator(), lines).strip();
    }

    private String componentLine(
            GitLabFrontendReachabilityComponent component,
            UiExplorerInitialSourceProjection projection,
            Map<String, String> componentReferences,
            Map<String, String> dependencyReferences
    ) {
        var details = new ArrayList<String>();
        details.add("sliceRef=`" + safe(component.componentId()) + "`");
        details.add("initial=" + (projection.embeds(component) ? "EMBEDDED" : "ON_DEMAND"));
        details.add("symbol=`" + safe(component.symbol()) + "`");
        details.add("discovery=" + safe(component.discoveryKind()));
        details.add("status=" + safe(component.status()));
        details.add("source=`" + safe(component.sourcePath()) + "`");
        if (StringUtils.hasText(component.templatePath())) {
            details.add("template=`" + safe(component.templatePath()) + "`");
        }
        var entries = component.entrySymbols().stream()
                .map(GitLabTypeScriptSymbolCandidate::symbolName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!entries.isEmpty()) {
            details.add("entries=" + compact(entries, 12));
        }
        var children = component.childComponentIds().stream()
                .map(id -> componentReferences.getOrDefault(id, id))
                .toList();
        if (!children.isEmpty()) {
            details.add("children=" + String.join(",", children));
        }
        var dependencies = component.dependencyIds().stream()
                .filter(dependencyReferences::containsKey)
                .map(dependencyReferences::get)
                .toList();
        if (!dependencies.isEmpty()) {
            details.add("dependencies=" + String.join(",", dependencies));
        }
        return "- [" + componentReferences.get(component.componentId()) + "] " + String.join("; ", details);
    }

    private String dependencyLine(
            GitLabFrontendReachabilityDependency dependency,
            UiExplorerInitialSourceProjection projection,
            Map<String, String> dependencyReferences
    ) {
        var details = new ArrayList<String>();
        details.add("sliceRef=`" + safe(dependency.dependencyId()) + "`");
        details.add("initial=" + initialState(dependency, projection));
        details.add("symbol=`" + safe(dependency.symbol()) + "`");
        details.add("kind=" + dependency.kind());
        details.add("category=" + dependency.category());
        details.add("status=" + safe(dependency.status()));
        var source = StringUtils.hasText(dependency.sourcePath())
                ? dependency.sourcePath()
                : dependency.moduleSpecifier();
        if (StringUtils.hasText(source)) {
            details.add("source=`" + safe(source) + "`");
        }
        if (!dependency.methods().isEmpty()) {
            details.add("members=" + compact(dependency.methods(), 12));
        }
        var downstream = dependency.downstreamDependencyIds().stream()
                .filter(dependencyReferences::containsKey)
                .map(dependencyReferences::get)
                .toList();
        if (!downstream.isEmpty()) {
            details.add("downstream=" + String.join(",", downstream));
        }
        return "- [" + dependencyReferences.get(dependency.dependencyId()) + "] " + String.join("; ", details);
    }

    private String supportingDependencyLine(
            GitLabFrontendReachabilityDependency dependency,
            UiExplorerInitialSourceProjection projection,
            Map<String, String> dependencyReferences
    ) {
        return "- [" + dependencyReferences.get(dependency.dependencyId()) + "] "
                + "sliceRef=`" + safe(dependency.dependencyId()) + "`; "
                + "initial=" + initialState(dependency, projection) + "; "
                + "symbol=`" + safe(dependency.symbol()) + "`; "
                + "kind=" + dependency.kind() + "; "
                + "category=" + dependency.category() + "; "
                + "source=`" + safe(dependency.sourcePath()) + "`";
    }

    private String initialState(
            GitLabFrontendReachabilityDependency dependency,
            UiExplorerInitialSourceProjection projection
    ) {
        if (projection.embeds(dependency)) {
            return "EMBEDDED";
        }
        return StringUtils.hasText(dependency.sourcePath()) ? "ON_DEMAND" : "EXTERNAL";
    }

    private boolean materialDependency(GitLabFrontendReachabilityDependency dependency) {
        return dependency.kind() != GitLabFrontendReachabilityDependencyKind.RXJS
                && dependency.category() != GitLabFrontendReachabilityDependencyCategory.FRAMEWORK
                && dependency.category() != GitLabFrontendReachabilityDependencyCategory.DATA_MODEL;
    }

    private String compact(List<String> values, int maximum) {
        var shown = values.stream().limit(maximum).map(this::safe).toList();
        return String.join(",", shown) + (values.size() > maximum
                ? ",...+" + (values.size() - maximum)
                : "");
    }

    private String routeOutline(String readableOutline) {
        if (!StringUtils.hasText(readableOutline)) {
            return "";
        }
        var boundaries = List.of(
                "\n## Component breadth-first traversal",
                "\n# Components BFS",
                "\n## Functional and supporting dependencies",
                "\n# Functional dependencies"
        );
        var boundary = boundaries.stream()
                .mapToInt(readableOutline::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(readableOutline.length());
        return readableOutline.substring(0, boundary).strip();
    }

    private String safe(String value) {
        return value != null
                ? value.replace("\r", " ").replace("\n", " ").replace("`", "'")
                : "";
    }
}
