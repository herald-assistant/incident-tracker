package pl.mkn.tdw.features.configdriftviewer.ai.model;

import java.util.List;

public record ConfigDriftViewerFunctionalImpact(
        String impactId,
        String affectedFunctionality,
        String impact,
        ConfigDriftViewerAiConfidence confidence,
        boolean hypothesis,
        List<String> systemIds,
        List<String> differenceIds,
        List<String> findingIds,
        List<String> contextIds,
        List<String> codeGroundingIds
) {

    public ConfigDriftViewerFunctionalImpact {
        systemIds = copy(systemIds);
        differenceIds = copy(differenceIds);
        findingIds = copy(findingIds);
        contextIds = copy(contextIds);
        codeGroundingIds = copy(codeGroundingIds);
    }

    private static List<String> copy(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }
}
