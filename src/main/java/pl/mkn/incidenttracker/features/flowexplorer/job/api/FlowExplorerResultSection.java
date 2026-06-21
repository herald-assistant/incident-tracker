package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import java.util.List;

public record FlowExplorerResultSection(
        FlowExplorerResultSectionId id,
        String title,
        FlowExplorerResultSectionMode mode,
        String markdown,
        List<String> sourceRefs,
        List<String> visibilityLimits,
        List<String> openQuestions
) {

    public FlowExplorerResultSection {
        title = title != null ? title : (id != null ? id.title() : "");
        mode = mode != null ? mode : FlowExplorerResultSectionMode.COMPACT;
        markdown = markdown != null ? markdown : "";
        sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        openQuestions = openQuestions != null ? List.copyOf(openQuestions) : List.of();
    }

    public FlowExplorerResultSection withMode(FlowExplorerResultSectionModeAssignment assignment) {
        return new FlowExplorerResultSection(
                assignment.id(),
                assignment.title(),
                assignment.mode(),
                markdown,
                sourceRefs,
                visibilityLimits,
                openQuestions
        );
    }
}
