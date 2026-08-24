package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.Map;

public record DeliveryPromptPreparation(
        String prompt,
        Map<String, String> artifacts
) {

    public DeliveryPromptPreparation {
        artifacts = artifacts != null ? Map.copyOf(artifacts) : Map.of();
    }
}
