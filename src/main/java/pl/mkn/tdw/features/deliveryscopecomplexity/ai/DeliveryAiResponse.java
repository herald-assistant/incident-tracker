package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import java.util.List;

public record DeliveryAiResponse(
        String classification,
        DeliveryScopeDimensions dimensions,
        double confidence,
        List<String> evidenceSummary,
        List<String> qualityFlags,
        List<String> visibilityLimits
) {

    public DeliveryAiResponse {
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        qualityFlags = qualityFlags != null ? List.copyOf(qualityFlags) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
