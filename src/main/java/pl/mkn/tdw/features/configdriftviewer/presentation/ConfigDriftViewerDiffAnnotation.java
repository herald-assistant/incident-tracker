package pl.mkn.tdw.features.configdriftviewer.presentation;

import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;

import java.util.List;

public record ConfigDriftViewerDiffAnnotation(
        String sourceId,
        ConfigDriftViewerDiffAnnotationKind kind,
        String comment,
        ConfigDriftViewerAiConfidence confidence,
        boolean hypothesis,
        List<String> differenceIds,
        List<String> findingIds
) {

    public ConfigDriftViewerDiffAnnotation {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        findingIds = findingIds != null ? List.copyOf(findingIds) : List.of();
    }
}
