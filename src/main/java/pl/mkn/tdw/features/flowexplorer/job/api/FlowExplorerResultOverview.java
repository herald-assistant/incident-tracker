package pl.mkn.tdw.features.flowexplorer.job.api;

import java.util.List;

public record FlowExplorerResultOverview(
        String markdown,
        String confidence,
        List<String> sourceRefs
) {

    public FlowExplorerResultOverview {
        markdown = markdown != null ? markdown : "";
        confidence = confidence != null ? confidence : "low";
        sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
    }
}
