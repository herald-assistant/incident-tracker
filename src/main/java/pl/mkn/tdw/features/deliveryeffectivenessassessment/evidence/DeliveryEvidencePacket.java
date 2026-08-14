package pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;

import java.util.List;
import java.util.Map;

public record DeliveryEvidencePacket(
        DeliveryUnit unit,
        Map<String, String> artifacts,
        boolean scorable,
        boolean mechanicallyExcluded,
        List<String> visibilityLimits
) {

    public DeliveryEvidencePacket {
        artifacts = artifacts != null ? Map.copyOf(artifacts) : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
