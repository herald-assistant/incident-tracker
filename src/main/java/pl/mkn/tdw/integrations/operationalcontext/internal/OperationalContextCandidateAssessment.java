package pl.mkn.tdw.integrations.operationalcontext.internal;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogPreviewViolation;

import java.util.List;

public record OperationalContextCandidateAssessment(
        List<OperationalContextCatalogPreviewViolation> violations
) {
    public OperationalContextCandidateAssessment {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
