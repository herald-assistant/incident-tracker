package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import java.util.List;

public record DeliveryScopeMergeRequestResponse(
        String identity,
        String projectPath,
        Long iid,
        String title,
        String webUrl,
        String mergedAt,
        Long authorId,
        String authorName,
        List<String> changedPaths
) {

    public DeliveryScopeMergeRequestResponse {
        changedPaths = changedPaths != null ? List.copyOf(changedPaths) : List.of();
    }

    public DeliveryScopeMergeRequestResponse(
            String identity,
            String projectPath,
            Long iid,
            String title,
            String webUrl,
            String mergedAt,
            List<String> changedPaths
    ) {
        this(identity, projectPath, iid, title, webUrl, mergedAt, null, "", changedPaths);
    }
}
