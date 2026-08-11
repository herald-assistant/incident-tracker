package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationalContextCatalogValidationService {

    static final String FINGERPRINT_ALGORITHM = "opctx-finding-v1";

    private final OperationalContextValidationBaselineProvider baselineProvider;
    private final OperationalContextReadModelValidator validator = new OperationalContextReadModelValidator();

    public ValidationReport validate(OperationalContextCatalog catalog) {
        var findings = validator.validate(catalog);
        var fingerprinted = findings.stream()
                .map(finding -> new FingerprintedFinding(fingerprint(finding), finding))
                .toList();
        return new ValidationReport(findings, fingerprinted);
    }

    CommitDecision validateForCommit(
            OperationalContextCatalog candidate,
            List<ValidationFinding> currentFindings
    ) {
        return validateFindingsForCommit(validate(candidate), currentFindings);
    }

    CommitDecision validateFindingsForCommit(
            ValidationReport report,
            List<ValidationFinding> currentFindings
    ) {
        var baseline = baselineProvider.load().byFingerprint();
        var current = byFingerprint(currentFindings);
        var violations = new ArrayList<CommitViolation>();

        for (var fingerprinted : report.fingerprintedFindings()) {
            var finding = fingerprinted.finding();
            var candidateSeverity = severityRank(finding.severity());
            var baselineEntry = baseline.get(fingerprinted.fingerprint());
            var currentFinding = current.get(fingerprinted.fingerprint());

            if (baselineEntry != null && !baselineEntry.ruleCode().equals(finding.code())) {
                violations.add(new CommitViolation(
                        "BASELINE_RULE_MISMATCH",
                        fingerprinted.fingerprint(),
                        finding.code(),
                        finding.severity()
                ));
                continue;
            }
            if (baselineEntry != null && candidateSeverity > severityRank(baselineEntry.severity())) {
                violations.add(new CommitViolation(
                        "SEVERITY_INCREASE_FROM_BASELINE",
                        fingerprinted.fingerprint(),
                        finding.code(),
                        finding.severity()
                ));
                continue;
            }
            if (candidateSeverity < severityRank("error")) {
                continue;
            }
            if (baselineEntry == null) {
                violations.add(new CommitViolation(
                        "NEW_UNCLASSIFIED_ERROR",
                        fingerprinted.fingerprint(),
                        finding.code(),
                        finding.severity()
                ));
                continue;
            }
            if (currentFinding == null) {
                violations.add(new CommitViolation(
                        "REINTRODUCED_ERROR",
                        fingerprinted.fingerprint(),
                        finding.code(),
                        finding.severity()
                ));
                continue;
            }
            if (candidateSeverity > severityRank(currentFinding.finding().severity())) {
                violations.add(new CommitViolation(
                        "SEVERITY_INCREASE_FROM_CURRENT",
                        fingerprinted.fingerprint(),
                        finding.code(),
                        finding.severity()
                ));
            }
        }
        return new CommitDecision(report, violations);
    }

    List<OperationalContextValidationBaselineEntry> baselineEntries(OperationalContextCatalog catalog) {
        return validate(catalog).fingerprintedFindings().stream()
                .map(finding -> new OperationalContextValidationBaselineEntry(
                        finding.fingerprint(),
                        finding.finding().code(),
                        finding.finding().severity().toLowerCase(Locale.ROOT)
                ))
                .distinct()
                .sorted(Comparator.comparing(OperationalContextValidationBaselineEntry::fingerprint))
                .toList();
    }

    public String fingerprint(ValidationFinding finding) {
        var references = finding.sourceRefs().stream()
                .map(this::canonicalReference)
                .sorted()
                .toList();
        var canonical = finding.code() + "\n" + String.join("\n", references);
        return sha256(canonical);
    }

    private Map<String, FingerprintedFinding> byFingerprint(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, FingerprintedFinding>();
        if (findings != null) {
            for (var finding : findings) {
                var fingerprinted = new FingerprintedFinding(fingerprint(finding), finding);
                result.putIfAbsent(fingerprinted.fingerprint(), fingerprinted);
            }
        }
        return Map.copyOf(result);
    }

    private String canonicalReference(SourceRef reference) {
        return String.join("|",
                logicalDocument(reference.file()),
                normalize(reference.entityType()),
                normalize(reference.entityId()),
                logicalFieldPath(reference.fieldPath())
        );
    }

    private String logicalDocument(String source) {
        if (!StringUtils.hasText(source)) {
            return "unknown";
        }
        var normalized = source.replace('\\', '/');
        var separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String logicalFieldPath(String fieldPath) {
        if (!StringUtils.hasText(fieldPath)) {
            return "$";
        }
        return fieldPath.trim().replaceAll("\\[\\d+]", "[]");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static int severityRank(String severity) {
        if (!StringUtils.hasText(severity)) {
            return -1;
        }
        return switch (severity.trim().toLowerCase(Locale.ROOT)) {
            case "info" -> 0;
            case "warning" -> 1;
            case "error" -> 2;
            default -> -1;
        };
    }

    public record FingerprintedFinding(
            String fingerprint,
            ValidationFinding finding
    ) {
    }

    public record ValidationReport(
            List<ValidationFinding> findings,
            List<FingerprintedFinding> fingerprintedFindings
    ) {
        public ValidationReport {
            findings = findings == null ? List.of() : List.copyOf(findings);
            fingerprintedFindings = fingerprintedFindings == null ? List.of() : List.copyOf(fingerprintedFindings);
        }
    }

    record CommitDecision(
            ValidationReport report,
            List<CommitViolation> violations
    ) {
        CommitDecision {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        boolean allowed() {
            return violations.isEmpty();
        }
    }

    record CommitViolation(
            String code,
            String fingerprint,
            String ruleCode,
            String severity
    ) {
    }
}
