package pl.mkn.tdw.features.runtimeconfigurationverification.presentation;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConfidence;

import java.util.List;

public record RuntimeConfigurationDiffAnnotation(
        String sourceId,
        RuntimeConfigurationDiffAnnotationKind kind,
        String comment,
        RuntimeConfigurationAiConfidence confidence,
        boolean hypothesis,
        List<String> differenceIds,
        List<String> findingIds
) {

    public RuntimeConfigurationDiffAnnotation {
        differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        findingIds = findingIds != null ? List.copyOf(findingIds) : List.of();
    }
}
