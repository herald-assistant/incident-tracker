package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogValidationService.FingerprintedFinding;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogValidationService.ValidationReport;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextCatalogValidationServiceTest {

    @Test
    void shouldKeepLogicalFingerprintStableAcrossLocalPathsMessagesAndListIndexes() {
        var service = service(List.of());
        var first = finding(
                "error",
                "CRM_REFERENCE_RULE",
                "CRM reference needs review.",
                "C:/crm-local-copy/operational-context/systems.yml",
                "systems[0].references.repositories[0]"
        );
        var second = finding(
                "warning",
                "CRM_REFERENCE_RULE",
                "Different operator-facing explanation.",
                "classpath:/operational-context/systems.yml",
                "systems[7].references.repositories[9]"
        );

        assertEquals(service.fingerprint(first), service.fingerprint(second));
    }

    @Test
    void shouldAllowOnlyKnownUnchangedErrorsAndNewWarnings() {
        var knownError = finding(
                "error",
                "CRM_KNOWN_REFERENCE",
                "Known CRM reference finding.",
                "systems.yml",
                "systems[id=crm-customer].references"
        );
        var fingerprint = service(List.of()).fingerprint(knownError);
        var service = service(List.of(entry(fingerprint, knownError.code(), "error")));

        assertTrue(service.validateFindingsForCommit(report(service, knownError), List.of(knownError)).allowed());

        var newWarning = finding(
                "warning",
                "CRM_OPERATOR_HINT",
                "CRM operator hint.",
                "systems.yml",
                "systems[id=crm-contact].notes"
        );
        assertTrue(service.validateFindingsForCommit(report(service, knownError, newWarning), List.of(knownError))
                .allowed());
    }

    @Test
    void shouldBlockNewWorseAndReintroducedErrors() {
        var knownWarning = finding(
                "warning",
                "CRM_REFERENCE_SEVERITY",
                "Known CRM warning.",
                "systems.yml",
                "systems[id=crm-customer].references"
        );
        var knownError = finding(
                "error",
                "CRM_ACCEPTED_ERROR",
                "Accepted CRM error.",
                "systems.yml",
                "systems[id=crm-contact].references"
        );
        var bootstrap = service(List.of());
        var service = service(List.of(
                entry(bootstrap.fingerprint(knownWarning), knownWarning.code(), "warning"),
                entry(bootstrap.fingerprint(knownError), knownError.code(), "error")
        ));

        var worsened = new ValidationFinding(
                "error",
                knownWarning.code(),
                "CRM warning became an error.",
                knownWarning.sourceRefs()
        );
        var worseDecision = service.validateFindingsForCommit(
                report(service, worsened),
                List.of(knownWarning)
        );
        assertEquals("SEVERITY_INCREASE_FROM_BASELINE", worseDecision.violations().get(0).code());

        var newError = finding(
                "error",
                "CRM_NEW_ERROR",
                "New CRM error.",
                "systems.yml",
                "systems[id=crm-case].references"
        );
        var newDecision = service.validateFindingsForCommit(report(service, newError), List.of());
        assertEquals("NEW_UNCLASSIFIED_ERROR", newDecision.violations().get(0).code());

        var reintroduced = service.validateFindingsForCommit(report(service, knownError), List.of());
        assertEquals("REINTRODUCED_ERROR", reintroduced.violations().get(0).code());
    }

    @Test
    void shouldLoadAnonymousVersionedBaselineWithExpectedDistribution() {
        var baseline = new OperationalContextValidationBaselineLoader(
                new ObjectMapper(),
                new DefaultResourceLoader()
        ).load();

        assertEquals(1, baseline.schemaVersion());
        assertEquals(OperationalContextCatalogValidationService.FINGERPRINT_ALGORITHM,
                baseline.fingerprintAlgorithm());
        assertEquals(101, baseline.findings().size());
        assertEquals(101, baseline.byFingerprint().size());
        assertEquals(98, baseline.findings().stream().filter(entry -> "error".equals(entry.severity())).count());
        assertEquals(3, baseline.findings().stream().filter(entry -> "warning".equals(entry.severity())).count());
        assertFalse(baseline.findings().stream().anyMatch(entry -> entry.fingerprint().contains("crm")));
    }

    private static OperationalContextCatalogValidationService service(
            List<OperationalContextValidationBaselineEntry> entries
    ) {
        OperationalContextValidationBaselineProvider provider = () -> new OperationalContextValidationBaseline(
                1,
                OperationalContextCatalogValidationService.FINGERPRINT_ALGORITHM,
                entries
        );
        return new OperationalContextCatalogValidationService(provider);
    }

    private static OperationalContextValidationBaselineEntry entry(
            String fingerprint,
            String ruleCode,
            String severity
    ) {
        return new OperationalContextValidationBaselineEntry(fingerprint, ruleCode, severity);
    }

    private static ValidationReport report(
            OperationalContextCatalogValidationService service,
            ValidationFinding... findings
    ) {
        var values = List.of(findings);
        return new ValidationReport(
                values,
                values.stream()
                        .map(finding -> new FingerprintedFinding(service.fingerprint(finding), finding))
                        .toList()
        );
    }

    private static ValidationFinding finding(
            String severity,
            String code,
            String message,
            String file,
            String fieldPath
    ) {
        return new ValidationFinding(
                severity,
                code,
                message,
                List.of(new SourceRef(
                        file,
                        "system",
                        "crm-customer",
                        fieldPath,
                        "crm-reference"
                ))
        );
    }
}
