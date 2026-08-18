package pl.mkn.tdw.features.deliverycomplexityassessment.evidence;

import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        artifacts = artifacts != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifacts))
                : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
