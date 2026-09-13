package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;
import java.util.Map;

public record OperationalContextCatalogMutationPreview(
        String type,
        String id,
        String baseDigest,
        Map<String, Object> candidatePayload,
        List<OperationalContextCatalogPreviewViolation> violations
) {
    public OperationalContextCatalogMutationPreview {
        candidatePayload = OperationalContextImmutableValues.copyMap(candidatePayload);
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
