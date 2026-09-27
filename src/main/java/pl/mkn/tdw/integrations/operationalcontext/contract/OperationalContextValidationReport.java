package pl.mkn.tdw.integrations.operationalcontext.contract;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.ValidationFinding;

import java.util.List;

public record OperationalContextValidationReport(
        List<ValidationFinding> findings,
        List<FingerprintedFinding> fingerprintedFindings
) {
    public OperationalContextValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
        fingerprintedFindings = fingerprintedFindings == null ? List.of() : List.copyOf(fingerprintedFindings);
    }

    public record FingerprintedFinding(String fingerprint, ValidationFinding finding) {
    }
}
