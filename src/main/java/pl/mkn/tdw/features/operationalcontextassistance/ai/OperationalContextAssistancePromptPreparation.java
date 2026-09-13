package pl.mkn.tdw.features.operationalcontextassistance.ai;

import java.util.Map;
import java.util.Set;

public record OperationalContextAssistancePromptPreparation(
        String prompt,
        Map<String, String> artifacts,
        Set<String> allowedSourceRefs
) {
    public OperationalContextAssistancePromptPreparation {
        artifacts = artifacts != null ? Map.copyOf(artifacts) : Map.of();
        allowedSourceRefs = allowedSourceRefs != null ? Set.copyOf(allowedSourceRefs) : Set.of();
    }
}
