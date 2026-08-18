package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api;

import java.util.List;

public record DeliveryAssessmentMergeRequestResponse(
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

    public DeliveryAssessmentMergeRequestResponse {
        changedPaths = changedPaths != null ? List.copyOf(changedPaths) : List.of();
    }

    public DeliveryAssessmentMergeRequestResponse(
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
