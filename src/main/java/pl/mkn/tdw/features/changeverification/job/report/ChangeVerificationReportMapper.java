package pl.mkn.tdw.features.changeverification.job.report;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationExecutionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ChangeVerificationReportMapper {

    public static final String SECTION_COMPLIANCE = "CHANGE_COMPLIANCE";
    public static final String SECTION_SMOKE_PACK = "SMOKE_PACK";
    public static final String SECTION_EXECUTION = "SMOKE_EXECUTION";

    private ChangeVerificationReportMapper() {
    }

    public static AnalysisReport toReport(ChangeVerificationResultResponse result) {
        if (result == null) {
            return null;
        }

        var compliance = result.compliance();
        var smokePack = result.smokePack();
        var execution = result.execution();
        var sections = List.of(
                new AnalysisReportSection(
                        SECTION_COMPLIANCE,
                        "Change alignment",
                        0,
                        complianceMarkdown(result),
                        complianceMeta(result)
                ),
                new AnalysisReportSection(
                        SECTION_SMOKE_PACK,
                        "Smoke pack",
                        1,
                        smokePackMarkdown(smokePack),
                        smokePackMeta(smokePack)
                ),
                new AnalysisReportSection(
                        SECTION_EXECUTION,
                        "Execution readiness",
                        2,
                        executionMarkdown(execution),
                        executionMeta(execution)
                )
        );

        return new AnalysisReport(
                "change-verification-" + fallback(result.issueKey(), "result"),
                "Change Verification: " + fallback(result.issueKey(), result.issueUrl(), "change"),
                subHeader(result),
                markdownSummary(result),
                sections,
                reportMeta(result)
        );
    }

    private static String subHeader(ChangeVerificationResultResponse result) {
        var parts = new ArrayList<String>();
        add(parts, "Compliance " + safeStatus(result.compliance() != null ? result.compliance().status() : null));
        add(parts, "Smoke " + safeStatus(result.smokePack() != null ? result.smokePack().status() : null));
        add(parts, "Execution " + safeStatus(result.execution() != null ? result.execution().status() : null));
        return String.join(" | ", parts);
    }

    private static String markdownSummary(ChangeVerificationResultResponse result) {
        var lines = new ArrayList<String>();
        lines.add("Run sprawdzil zgodnosc zmiany z materialem zrodlowym i przygotowal rezultat do review release.");
        if (result.compliance() != null) {
            lines.add("- Compliance: `" + safeStatus(result.compliance().status()) + "` with "
                    + result.compliance().findings().size() + " finding(s).");
        }
        if (result.smokePack() != null) {
            lines.add("- Smoke pack: `" + safeStatus(result.smokePack().status()) + "` with "
                    + result.smokePack().tests().size() + " test(s).");
        }
        if (result.execution() != null) {
            lines.add("- Execution: `" + safeStatus(result.execution().status()) + "`.");
        }
        return String.join("\n", lines);
    }

    private static String complianceMarkdown(ChangeVerificationResultResponse result) {
        var compliance = result.compliance();
        if (compliance == null) {
            return "Compliance result is not available.";
        }

        var lines = new ArrayList<String>();
        lines.add("### Status");
        lines.add("`" + safeStatus(compliance.status()) + "`");
        lines.add("");
        lines.add("### Scope");
        lines.add("- Story compliance: " + yesNo(compliance.storyComplianceRequested()));
        lines.add("- Instruction compliance: " + yesNo(compliance.instructionComplianceRequested()));

        if (!compliance.findings().isEmpty()) {
            lines.add("");
            lines.add("### Findings");
            for (var finding : compliance.findings()) {
                lines.add("- **[" + finding.severity() + "] " + fallback(finding.summary(), "Finding") + "**");
                addIndented(lines, finding.details());
                if (StringUtils.hasText(finding.source())) {
                    addIndented(lines, "Source: `" + finding.source().trim() + "`");
                }
                if (StringUtils.hasText(finding.suggestedAction())) {
                    addIndented(lines, "Suggested action: " + finding.suggestedAction().trim());
                }
            }
        } else {
            lines.add("");
            lines.add("### Findings");
            lines.add("No compliance findings were returned.");
        }

        if (!compliance.suggestedActions().isEmpty()) {
            lines.add("");
            lines.add("### Recommended actions");
            compliance.suggestedActions().forEach(action -> lines.add("- " + action));
        }
        return compactMarkdown(lines);
    }

    private static AnalysisReportMeta complianceMeta(ChangeVerificationResultResponse result) {
        var compliance = result.compliance();
        if (compliance == null) {
            return AnalysisReportMeta.empty();
        }

        var references = new ArrayList<AnalysisReportReference>();
        var gaps = new ArrayList<String>();
        var openQuestions = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var finding : compliance.findings()) {
            for (var reference : finding.references()) {
                if (StringUtils.hasText(reference)) {
                    references.add(new AnalysisReportReference(
                            "change-source",
                            fallback(finding.id(), finding.source(), "reference"),
                            reference.trim(),
                            fallback(finding.summary(), "")
                    ));
                }
            }
            if ("VISIBILITY".equalsIgnoreCase(finding.source())) {
                add(gaps, finding.summary());
            }
            if (isMaterialFinding(finding)) {
                add(openQuestions, "Czy zakres story/instrukcji wymaga korekty: " + finding.summary());
            }
            if (isWarningFinding(finding)) {
                add(warnings, "[" + finding.severity() + "] " + finding.summary());
            }
        }

        return new AnalysisReportMeta(
                references,
                compliance.visibilityLimits(),
                distinct(openQuestions),
                distinct(gaps),
                result.smokePack() != null ? normalizeConfidence(result.smokePack().confidence()) : "",
                distinct(warnings)
        );
    }

    private static String smokePackMarkdown(ChangeVerificationSmokePackResponse smokePack) {
        if (smokePack == null) {
            return "Smoke pack was not generated.";
        }

        var lines = new ArrayList<String>();
        lines.add("### Status");
        lines.add("`" + safeStatus(smokePack.status()) + "`");
        if (StringUtils.hasText(smokePack.postmanCollectionName())) {
            lines.add("");
            lines.add("Collection: `" + smokePack.postmanCollectionName().trim() + "`");
        }

        if (!smokePack.tests().isEmpty()) {
            lines.add("");
            lines.add("### Smoke tests");
            for (var test : smokePack.tests()) {
                lines.add("- **" + fallback(test.id(), "test") + ": " + fallback(test.name(), "Smoke test") + "**");
                addIndented(lines, "`" + fallback(test.method(), "HTTP") + " " + fallback(test.path(), "/") + "`");
                addIndented(lines, fallback(test.riskCovered(), test.purpose()));
                addIndented(lines, "Review status: `" + fallback(test.reviewStatus(), "NEEDS_REVIEW") + "`");
                if (!test.responseAssertions().isEmpty()) {
                    addIndented(lines, "Assertions: " + test.responseAssertions().size());
                }
                if (test.cleanup() != null && StringUtils.hasText(test.cleanup().strategy())) {
                    addIndented(lines, "Cleanup: `" + test.cleanup().strategy().trim() + "`");
                }
            }
        } else {
            lines.add("");
            lines.add("### Smoke tests");
            lines.add("No smoke tests were generated.");
        }

        if (!smokePack.suggestedActions().isEmpty()) {
            lines.add("");
            lines.add("### Recommended actions");
            smokePack.suggestedActions().forEach(action -> lines.add("- " + action));
        }
        return compactMarkdown(lines);
    }

    private static AnalysisReportMeta smokePackMeta(ChangeVerificationSmokePackResponse smokePack) {
        if (smokePack == null) {
            return AnalysisReportMeta.empty();
        }

        var references = new ArrayList<AnalysisReportReference>();
        var gaps = new ArrayList<String>();
        var openQuestions = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var test : smokePack.tests()) {
            for (var ref : test.sourceRefs()) {
                if (StringUtils.hasText(ref)) {
                    references.add(new AnalysisReportReference(
                            "smoke-source",
                            fallback(test.id(), test.name(), "smoke test"),
                            ref.trim(),
                            fallback(test.riskCovered(), test.purpose(), "")
                    ));
                }
            }
            if (!"READY".equalsIgnoreCase(fallback(test.reviewStatus(), ""))) {
                add(gaps, fallback(test.id(), test.name(), "Smoke test") + " requires review before execution.");
            }
            if (test.cleanup() == null || !"NONE".equalsIgnoreCase(fallback(test.cleanup().strategy(), ""))) {
                add(openQuestions, "Czy cleanup dla " + fallback(test.id(), test.name(), "smoke test")
                        + " jest bezpieczny i wykonywalny endpointem aplikacyjnym?");
            }
        }
        if (!"READY".equalsIgnoreCase(fallback(smokePack.status(), ""))) {
            add(warnings, "Smoke pack status is " + safeStatus(smokePack.status()) + ".");
        }
        return new AnalysisReportMeta(
                references,
                smokePack.visibilityLimits(),
                distinct(openQuestions),
                distinct(gaps),
                normalizeConfidence(smokePack.confidence()),
                distinct(warnings)
        );
    }

    private static String executionMarkdown(ChangeVerificationExecutionResponse execution) {
        if (execution == null || !execution.requested()) {
            return "Smoke execution was not requested in this run.";
        }

        var lines = new ArrayList<String>();
        lines.add("### Status");
        lines.add("`" + safeStatus(execution.status()) + "`");
        if (!execution.executedTestIds().isEmpty()) {
            lines.add("");
            lines.add("Executed tests: `" + String.join("`, `", execution.executedTestIds()) + "`");
        }
        if (!execution.testResults().isEmpty()) {
            lines.add("");
            lines.add("### Test results");
            for (var testResult : execution.testResults()) {
                lines.add("- **" + fallback(testResult.testId(), testResult.name(), "test") + "**: `"
                        + safeStatus(testResult.status()) + "`");
                if (testResult.http() != null) {
                    addIndented(lines, fallback(testResult.http().method(), "HTTP") + " "
                            + fallback(testResult.http().url(), "")
                            + " -> " + (testResult.http().statusCode() != null
                            ? testResult.http().statusCode()
                            : "n/a")
                            + " in " + testResult.http().durationMillis() + "ms");
                }
                testResult.responseAssertions().forEach(assertion ->
                        addIndented(lines, assertion.status() + " - " + assertion.type() + " " + assertion.target()
                                + ": " + assertion.message()));
                if (testResult.cleanup() != null) {
                    addIndented(lines, "Cleanup: " + testResult.cleanup().status() + " - "
                            + testResult.cleanup().message());
                }
            }
        } else {
            lines.add("");
            lines.add("No smoke tests have been executed yet.");
        }
        return compactMarkdown(lines);
    }

    private static AnalysisReportMeta executionMeta(ChangeVerificationExecutionResponse execution) {
        if (execution == null) {
            return AnalysisReportMeta.empty();
        }
        var gaps = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        if (execution.requested() && execution.testResults().isEmpty()) {
            gaps.add("Execution was requested but waits for explicit operator run with base URL.");
        }
        if ("FAILED".equalsIgnoreCase(execution.status())) {
            warnings.add("At least one smoke test failed.");
        }
        if (StringUtils.hasText(execution.manualCleanupSql())) {
            warnings.add("Manual cleanup SQL was produced and requires operator-controlled execution.");
        }
        return new AnalysisReportMeta(
                List.of(),
                execution.visibilityLimits(),
                List.of(),
                gaps,
                "",
                warnings
        );
    }

    private static AnalysisReportMeta reportMeta(ChangeVerificationResultResponse result) {
        var references = new ArrayList<AnalysisReportReference>();
        references.add(new AnalysisReportReference(
                "jira",
                fallback(result.issueKey(), "Jira issue"),
                fallback(result.issueUrl(), result.issueKey(), ""),
                "Target issue verified by Change Verification."
        ));
        references.addAll(complianceMeta(result).references());
        if (result.smokePack() != null) {
            references.addAll(smokePackMeta(result.smokePack()).references());
        }

        var visibilityLimits = new ArrayList<String>();
        if (result.compliance() != null) {
            visibilityLimits.addAll(result.compliance().visibilityLimits());
        }
        if (result.smokePack() != null) {
            visibilityLimits.addAll(result.smokePack().visibilityLimits());
        }
        if (result.execution() != null) {
            visibilityLimits.addAll(result.execution().visibilityLimits());
        }

        var warnings = new ArrayList<String>();
        if (result.compliance() != null && !"PASSED".equalsIgnoreCase(fallback(result.compliance().status(), ""))) {
            add(warnings, "Compliance status is " + safeStatus(result.compliance().status()) + ".");
        }
        if (result.smokePack() != null && !"READY".equalsIgnoreCase(fallback(result.smokePack().status(), ""))) {
            add(warnings, "Smoke pack status is " + safeStatus(result.smokePack().status()) + ".");
        }
        return new AnalysisReportMeta(
                distinctReferences(references),
                distinct(visibilityLimits),
                List.of(),
                List.of(),
                result.smokePack() != null ? normalizeConfidence(result.smokePack().confidence()) : "",
                distinct(warnings)
        );
    }

    private static boolean isMaterialFinding(ChangeVerificationFindingResponse finding) {
        if (finding == null || finding.severity() == null) {
            return false;
        }
        var source = fallback(finding.source(), "").toUpperCase(Locale.ROOT);
        return ("STORY".equals(source) || "INSTRUCTIONS".equals(source))
                && ("MEDIUM".equals(finding.severity().name())
                || "HIGH".equals(finding.severity().name())
                || "BLOCKER".equals(finding.severity().name()));
    }

    private static boolean isWarningFinding(ChangeVerificationFindingResponse finding) {
        if (finding == null || finding.severity() == null) {
            return false;
        }
        return switch (finding.severity()) {
            case MEDIUM, HIGH, BLOCKER -> true;
            default -> false;
        };
    }

    private static String normalizeConfidence(String confidence) {
        if (!StringUtils.hasText(confidence)) {
            return "";
        }
        return confidence.trim().toLowerCase(Locale.ROOT);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String safeStatus(String status) {
        return fallback(status, "pending");
    }

    private static void addIndented(List<String> lines, String value) {
        if (StringUtils.hasText(value)) {
            lines.add("  - " + value.trim());
        }
    }

    private static void add(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(
                values.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList()
        ));
    }

    private static List<AnalysisReportReference> distinctReferences(List<AnalysisReportReference> references) {
        var seen = new LinkedHashSet<String>();
        var distinct = new ArrayList<AnalysisReportReference>();
        for (var reference : references) {
            if (reference == null) {
                continue;
            }
            var key = fallback(reference.type(), "") + "|" + fallback(reference.label(), "") + "|"
                    + fallback(reference.target(), "") + "|" + fallback(reference.description(), "");
            if (seen.add(key)) {
                distinct.add(reference);
            }
        }
        return List.copyOf(distinct);
    }

    private static String compactMarkdown(List<String> lines) {
        return String.join("\n", lines).replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String fallback(String primary, String secondary) {
        return fallback(primary, secondary, "");
    }

    private static String fallback(String primary, String secondary, String tertiary) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(secondary)) {
            return secondary.trim();
        }
        return StringUtils.hasText(tertiary) ? tertiary.trim() : "";
    }
}
