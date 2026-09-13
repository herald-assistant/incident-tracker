package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

record OperationalContextCandidateAssessment(
        List<OperationalContextCatalogPreviewViolation> violations
) {
    OperationalContextCandidateAssessment {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    boolean valid() {
        return violations.isEmpty();
    }
}
