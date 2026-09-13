package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

/** Read-only assessment of the complete catalog after an ordered batch. */
public record OperationalContextCatalogBatchMutationPreview(
        List<OperationalContextEditableEntity> entities,
        String expectedDigest,
        String candidateDigest,
        List<OperationalContextCatalogPreviewViolation> violations
) {
    public OperationalContextCatalogBatchMutationPreview {
        entities = entities == null ? List.of() : List.copyOf(entities);
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
