package pl.mkn.tdw.features.operationalcontextassistance.api;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogPreviewViolation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEditableEntity;

import java.util.List;

/** The complete resulting catalog assessment for the operator's current selection. */
public record OperationalContextAssistanceBatchPreview(
        String expectedDigest,
        String candidateDigest,
        boolean valid,
        List<OperationalContextEditableEntity> entities,
        List<OperationalContextCatalogPreviewViolation> violations
) {
    public OperationalContextAssistanceBatchPreview {
        entities = entities != null ? List.copyOf(entities) : List.of();
        violations = violations != null ? List.copyOf(violations) : List.of();
    }
}
