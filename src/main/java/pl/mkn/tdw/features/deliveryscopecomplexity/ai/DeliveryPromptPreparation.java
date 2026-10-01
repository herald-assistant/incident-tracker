package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.Map;

public record DeliveryPromptPreparation(
        String prompt,
        Map<String, String> artifacts,
        String effectiveSkill
) {

    public DeliveryPromptPreparation(String prompt, Map<String, String> artifacts) {
        this(prompt, artifacts, null);
    }

    public DeliveryPromptPreparation {
        artifacts = artifacts != null ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(artifacts)) : Map.of();
    }
}
