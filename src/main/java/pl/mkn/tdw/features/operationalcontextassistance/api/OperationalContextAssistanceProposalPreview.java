package pl.mkn.tdw.features.operationalcontextassistance.api;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogPreviewViolation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record OperationalContextAssistanceProposalPreview(
        int proposalIndex,
        ValidationStatus validationStatus,
        boolean valid,
        Map<String, Object> candidatePayload,
        List<OperationalContextCatalogPreviewViolation> violations,
        List<OperationalContextCatalogFieldError> fieldErrors
) {
    public OperationalContextAssistanceProposalPreview {
        candidatePayload = candidatePayload != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(candidatePayload)) : Map.of();
        violations = violations != null ? List.copyOf(violations) : List.of();
        fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public enum ValidationStatus { VALID, INVALID, DEFERRED }
}
