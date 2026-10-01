package pl.mkn.tdw.shared.ai;

import java.util.List;

public record AnalysisAiFinding(String behaviorId, String dimension, String fact,
                                List<String> references, List<String> dependencies) {
    public AnalysisAiFinding {
        references = references != null ? List.copyOf(references) : List.of();
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    }
}
