package pl.mkn.tdw.features.configdriftviewer.ai.model;

import java.util.List;

public record ConfigDriftViewerAiObservation(
        String observationId,
        ConfigDriftViewerAiObservationType type,
        String summary,
        String explanation,
        List<String> differenceIds,
        List<String> findingIds,
        List<String> contextIds,
        List<String> codeGroundingIds
) {

    public ConfigDriftViewerAiObservation {
        differenceIds = copy(differenceIds);
        findingIds = copy(findingIds);
        contextIds = copy(contextIds);
        codeGroundingIds = copy(codeGroundingIds);
    }

    public boolean grounded() {
        return !differenceIds.isEmpty() || !findingIds.isEmpty()
                || !contextIds.isEmpty() || !codeGroundingIds.isEmpty();
    }

    private static List<String> copy(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }
}
