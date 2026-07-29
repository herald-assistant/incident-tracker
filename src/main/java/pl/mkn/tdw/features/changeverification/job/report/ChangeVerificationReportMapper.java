package pl.mkn.tdw.features.changeverification.job.report;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ChangeVerificationReportMapper {

    public static final String SECTION_STORY_COMPLIANCE = ChangeVerificationReportSectionIds.STORY_COMPLIANCE;
    public static final String SECTION_INSTRUCTION_COMPLIANCE =
            ChangeVerificationReportSectionIds.INSTRUCTION_COMPLIANCE;

    private ChangeVerificationReportMapper() {
    }

    public static AnalysisReport toReport(ChangeVerificationResultResponse result) {
        return toReport(result, null);
    }

    public static AnalysisReport toReport(
            ChangeVerificationResultResponse result,
            AnalysisReport aiComplianceReport
    ) {
        if (result == null) {
            return null;
        }

        var fallbackSections = List.of(
                new AnalysisReportSection(
                        SECTION_STORY_COMPLIANCE,
                        "Story compliance",
                        0,
                        storyComplianceMarkdown(result),
                        storyComplianceMeta(result)
                ),
                new AnalysisReportSection(
                        SECTION_INSTRUCTION_COMPLIANCE,
                        "Instruction compliance",
                        1,
                        instructionComplianceMarkdown(result),
                        instructionComplianceMeta(result)
                )
        );
        var sections = fallbackSections.stream()
                .map(section -> mergeAiSection(aiComplianceReport, section))
                .toList();

        return new AnalysisReport(
                "change-verification-" + fallback(result.issueKey(), "result"),
                "Change Verification: " + fallback(result.issueKey(), result.issueUrl(), "change"),
                subHeader(result),
                markdownSummary(result),
                sections,
                mergeMeta(
                        aiComplianceReport != null ? aiComplianceReport.meta() : null,
                        reportMeta(result)
                )
        );
    }

    private static AnalysisReportSection mergeAiSection(
            AnalysisReport aiComplianceReport,
            AnalysisReportSection fallbackSection
    ) {
        var aiSection = findSection(aiComplianceReport, fallbackSection.id());
        if (aiSection == null || !StringUtils.hasText(aiSection.markdown())) {
            return fallbackSection;
        }
        return new AnalysisReportSection(
                fallbackSection.id(),
                StringUtils.hasText(aiSection.title()) ? aiSection.title().trim() : fallbackSection.title(),
                fallbackSection.order(),
                aiSection.markdown().trim(),
                mergeMeta(aiSection.meta(), fallbackSection.meta())
        );
    }

    private static AnalysisReportSection findSection(AnalysisReport report, String sectionId) {
        if (report == null || !StringUtils.hasText(sectionId)) {
            return null;
        }
        return report.sections().stream()
                .filter(section -> section != null && sectionId.equalsIgnoreCase(section.id()))
                .findFirst()
                .orElse(null);
    }

    private static AnalysisReportMeta mergeMeta(AnalysisReportMeta primary, AnalysisReportMeta fallback) {
        var primaryMeta = primary != null ? primary : AnalysisReportMeta.empty();
        var fallbackMeta = fallback != null ? fallback : AnalysisReportMeta.empty();

        var references = new ArrayList<AnalysisReportReference>();
        references.addAll(primaryMeta.references());
        references.addAll(fallbackMeta.references());

        var visibilityLimits = new ArrayList<String>();
        visibilityLimits.addAll(primaryMeta.visibilityLimits());
        visibilityLimits.addAll(fallbackMeta.visibilityLimits());

        var openQuestions = new ArrayList<String>();
        openQuestions.addAll(primaryMeta.openQuestions());
        openQuestions.addAll(fallbackMeta.openQuestions());

        var gaps = new ArrayList<String>();
        gaps.addAll(primaryMeta.gaps());
        gaps.addAll(fallbackMeta.gaps());

        var warnings = new ArrayList<String>();
        warnings.addAll(primaryMeta.warnings());
        warnings.addAll(fallbackMeta.warnings());

        return new AnalysisReportMeta(
                distinctReferences(references),
                distinct(visibilityLimits),
                distinct(openQuestions),
                distinct(gaps),
                StringUtils.hasText(primaryMeta.confidence())
                        ? normalizeConfidence(primaryMeta.confidence())
                        : normalizeConfidence(fallbackMeta.confidence()),
                distinct(warnings)
        );
    }

    private static String subHeader(ChangeVerificationResultResponse result) {
        return "Compliance " + safeStatus(result.compliance() != null ? result.compliance().status() : null);
    }

    private static String markdownSummary(ChangeVerificationResultResponse result) {
        var lines = new ArrayList<String>();
        lines.add("Run sprawdzil zgodnosc zmiany z materialem zrodlowym i przygotowal rezultat do review release.");
        if (result.compliance() != null) {
            lines.add("- Compliance: `" + safeStatus(result.compliance().status()) + "` with "
                    + result.compliance().findings().size() + " finding(s).");
        }
        return String.join("\n", lines);
    }

    private static String storyComplianceMarkdown(ChangeVerificationResultResponse result) {
        return complianceSectionMarkdown(
                result,
                SECTION_STORY_COMPLIANCE,
                "Story compliance",
                "Story compliance was not requested in this run.",
                "Jira story, acceptance criteria, comments and Confluence context"
        );
    }

    private static String instructionComplianceMarkdown(ChangeVerificationResultResponse result) {
        return complianceSectionMarkdown(
                result,
                SECTION_INSTRUCTION_COMPLIANCE,
                "Instruction compliance",
                "Instruction compliance was not requested in this run.",
                "Repository instructions, AGENTS.md, copilot-instructions and referenced instruction files"
        );
    }

    private static String complianceSectionMarkdown(
            ChangeVerificationResultResponse result,
            String scope,
            String title,
            String notRequestedText,
            String evidenceScope
    ) {
        var compliance = result.compliance();
        if (compliance == null) {
            return "Compliance result is not available.";
        }

        if (SECTION_STORY_COMPLIANCE.equals(scope) && !compliance.storyComplianceRequested()) {
            return notRequestedText;
        }
        if (SECTION_INSTRUCTION_COMPLIANCE.equals(scope) && !compliance.instructionComplianceRequested()) {
            return notRequestedText;
        }

        var checks = checksForScope(compliance.verificationChecks(), scope);
        var passedChecks = checks.stream().filter(ChangeVerificationReportMapper::isPassedCheck).toList();
        var attentionChecks = checks.stream().filter(check -> !isPassedCheck(check)).toList();
        var lines = new ArrayList<String>();
        lines.add("## Wynik weryfikacji");
        lines.add("**Status:** `" + safeStatus(compliance.status()) + "`"
                + " · **Potwierdzone:** " + passedChecks.size()
                + " · **Wymaga uwagi:** " + attentionChecks.size()
                + " · **Wszystkie kryteria:** " + checks.size());
        lines.add("Zakres: " + evidenceScope + ".");
        if (!checks.isEmpty()) {
            appendKeyTakeaways(lines, passedChecks, attentionChecks, compliance.suggestedActions());
        } else {
            lines.add("Analiza nie zwrocila kryteriow wymaganych przez aktualny kontrakt.");
        }

        if (!checks.isEmpty()) {
            if (!attentionChecks.isEmpty()) {
                lines.add("");
                lines.add("## Wymaga uwagi");
                lines.add("| Status | Kryterium | Wniosek | Rekomendowane działanie |");
                lines.add("| --- | --- | --- | --- |");
                for (var check : attentionChecks) {
                    appendAttentionTableRow(lines, check);
                }
            }

            if (!passedChecks.isEmpty()) {
                lines.add("");
                lines.add("## Potwierdzone wymagania");
                lines.add("| Wymaganie | Źródło | Co potwierdzono | Status |");
                lines.add("| --- | --- | --- | --- |");
                for (var check : passedChecks) {
                    appendPassedTableRow(lines, check);
                }
            }

            lines.add("");
            lines.add("## Szczegóły kryteriów");
            checks.forEach(check -> appendVerificationCheck(lines, check));
        } else {
            lines.add("");
            lines.add("Brak strukturalnych kryteriów dla sekcji `" + title + "`.");
        }

        var suggestedActions = compliance.suggestedActions().stream()
                .filter(ChangeVerificationReportMapper::meaningfulText)
                .toList();
        if (!suggestedActions.isEmpty()) {
            lines.add("");
            lines.add("## Rekomendowane działania");
            suggestedActions.forEach(action -> lines.add("- " + action.trim()));
        }
        return compactMarkdown(lines);
    }

    private static void appendKeyTakeaways(
            List<String> lines,
            List<ChangeVerificationVerificationCheckResponse> passedChecks,
            List<ChangeVerificationVerificationCheckResponse> attentionChecks,
            List<String> suggestedActions
    ) {
        lines.add("");
        lines.add("### Najważniejsze wnioski");
        if (!passedChecks.isEmpty()) {
            var check = passedChecks.get(0);
            lines.add("- **Potwierdzenie:** " + fallback(check.analysis(), check.expectedCriterion(), "Kryterium potwierdzone."));
        }
        if (!attentionChecks.isEmpty()) {
            var check = attentionChecks.get(0);
            lines.add("- **Ryzyko:** " + fallback(check.analysis(), check.expectedCriterion(), "Kryterium wymaga uwagi."));
        }
        var action = attentionChecks.stream()
                .map(ChangeVerificationVerificationCheckResponse::suggestedAction)
                .filter(ChangeVerificationReportMapper::meaningfulText)
                .findFirst()
                .orElseGet(() -> suggestedActions.stream()
                        .filter(ChangeVerificationReportMapper::meaningfulText)
                        .findFirst()
                        .orElse(""));
        if (StringUtils.hasText(action)) {
            lines.add("- **Działanie:** " + action.trim());
        }
    }

    private static void appendAttentionTableRow(
            List<String> lines,
            ChangeVerificationVerificationCheckResponse check
    ) {
        lines.add("| " + markdownTableCell(safeStatus(check.verificationStatus()))
                + " | " + markdownTableCell(fallback(check.expectedCriterion(), check.criterionQuote(), check.id()))
                + " | " + markdownTableCell(fallback(check.analysis(), check.verifiedAgainst(), "Brak wniosku."))
                + " | " + markdownTableCell(meaningfulText(check.suggestedAction())
                ? check.suggestedAction()
                : "Wymaga decyzji ownera.") + " |");
    }

    private static void appendPassedTableRow(
            List<String> lines,
            ChangeVerificationVerificationCheckResponse check
    ) {
        lines.add("| " + markdownTableCell(fallback(check.expectedCriterion(), check.criterionQuote(), check.id()))
                + " | " + markdownTableCell(fallback(check.criterionSource(), check.scope(), "Materiał źródłowy"))
                + " | " + markdownTableCell(fallback(check.analysis(), check.verifiedAgainst(), "Potwierdzone."))
                + " | " + markdownTableCell(safeStatus(check.verificationStatus())) + " |");
    }

    private static void appendVerificationCheck(
            List<String> lines,
            ChangeVerificationVerificationCheckResponse check
    ) {
        lines.add("");
        lines.add("### " + safeStatus(check.verificationStatus()) + " · "
                + fallback(check.expectedCriterion(), check.criterionQuote(), check.id()));
        if (StringUtils.hasText(check.criterionSource())) {
            lines.add("- **Źródło:** " + check.criterionSource().trim());
        }
        if (StringUtils.hasText(check.interpretationType())) {
            lines.add("- **Interpretacja:** `" + check.interpretationType().trim() + "`");
        }
        if (StringUtils.hasText(check.verifiedAgainst())) {
            lines.add("- **Zweryfikowano względem:** " + check.verifiedAgainst().trim());
        }
        if (StringUtils.hasText(check.analysis())) {
            lines.add("");
            lines.add(check.analysis().trim());
        }
        if (meaningfulText(check.criterionQuote())) {
            lines.add("");
            lines.add("#### Fragment materiału źródłowego");
            lines.add(quote(check.criterionQuote()));
        }
        if (meaningfulText(check.suggestedAction())) {
            lines.add("");
            lines.add("#### Rekomendowane działanie");
            lines.add(check.suggestedAction().trim());
        }
    }

    private static AnalysisReportMeta storyComplianceMeta(ChangeVerificationResultResponse result) {
        return complianceMeta(result, SECTION_STORY_COMPLIANCE);
    }

    private static AnalysisReportMeta instructionComplianceMeta(ChangeVerificationResultResponse result) {
        return complianceMeta(result, SECTION_INSTRUCTION_COMPLIANCE);
    }

    private static AnalysisReportMeta complianceMeta(ChangeVerificationResultResponse result, String scope) {
        var compliance = result.compliance();
        if (compliance == null) {
            return AnalysisReportMeta.empty();
        }

        var references = new ArrayList<AnalysisReportReference>();
        var gaps = new ArrayList<String>();
        var openQuestions = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var check : checksForScope(compliance.verificationChecks(), scope)) {
            for (var reference : check.evidenceRefs()) {
                if (StringUtils.hasText(reference)) {
                    references.add(new AnalysisReportReference(
                            scope.toLowerCase(Locale.ROOT),
                            fallback(check.id(), check.criterionSource(), "criterion"),
                            reference.trim(),
                            fallback(check.expectedCriterion(), check.analysis(), "")
                    ));
                }
            }
            gaps.addAll(check.gaps());
            if (!isPassedCheck(check)) {
                add(warnings, "[" + safeStatus(check.verificationStatus()) + "] "
                        + fallback(check.expectedCriterion(), check.id(), "criterion"));
            }
            if ("NOT_VERIFIED".equalsIgnoreCase(fallback(check.verificationStatus(), ""))) {
                add(openQuestions, "Jak potwierdzic kryterium: " + fallback(check.expectedCriterion(), check.id(), "criterion") + "?");
            }
        }
        for (var finding : findingsForScope(compliance.findings(), scope)) {
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
                "",
                distinct(warnings)
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
        references.addAll(storyComplianceMeta(result).references());
        references.addAll(instructionComplianceMeta(result).references());

        var visibilityLimits = new ArrayList<String>();
        if (result.compliance() != null) {
            visibilityLimits.addAll(result.compliance().visibilityLimits());
        }

        var warnings = new ArrayList<String>();
        if (result.compliance() != null && !"PASSED".equalsIgnoreCase(fallback(result.compliance().status(), ""))) {
            add(warnings, "Compliance status is " + safeStatus(result.compliance().status()) + ".");
        }
        return new AnalysisReportMeta(
                distinctReferences(references),
                distinct(visibilityLimits),
                List.of(),
                List.of(),
                "",
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

    private static List<ChangeVerificationVerificationCheckResponse> checksForScope(
            List<ChangeVerificationVerificationCheckResponse> checks,
            String scope
    ) {
        return checks.stream()
                .filter(check -> check != null && matchesScope(check.scope(), scope))
                .toList();
    }

    private static List<ChangeVerificationFindingResponse> findingsForScope(
            List<ChangeVerificationFindingResponse> findings,
            String scope
    ) {
        return findings.stream()
                .filter(finding -> finding != null && matchesFindingScope(finding.source(), scope))
                .toList();
    }

    private static boolean matchesScope(String value, String scope) {
        var normalized = fallback(value, "").toUpperCase(Locale.ROOT);
        if (SECTION_STORY_COMPLIANCE.equals(scope)) {
            return normalized.contains("STORY")
                    || normalized.contains("ACCEPTANCE")
                    || normalized.contains("JIRA")
                    || normalized.contains("CONFLUENCE");
        }
        if (SECTION_INSTRUCTION_COMPLIANCE.equals(scope)) {
            return normalized.contains("INSTRUCTION")
                    || normalized.contains("AGENTS")
                    || normalized.contains("COPILOT");
        }
        return normalized.equals(scope);
    }

    private static boolean matchesFindingScope(String value, String scope) {
        var normalized = fallback(value, "").toUpperCase(Locale.ROOT);
        if (SECTION_STORY_COMPLIANCE.equals(scope)) {
            return "STORY".equals(normalized)
                    || "ACCEPTANCE_CRITERIA".equals(normalized)
                    || "IMPLEMENTATION".equals(normalized)
                    || "VISIBILITY".equals(normalized);
        }
        if (SECTION_INSTRUCTION_COMPLIANCE.equals(scope)) {
            return "INSTRUCTIONS".equals(normalized);
        }
        return normalized.equals(scope);
    }

    private static boolean isPassedCheck(ChangeVerificationVerificationCheckResponse check) {
        return "PASSED".equalsIgnoreCase(fallback(check != null ? check.verificationStatus() : null, ""));
    }

    private static String quote(String value) {
        return "> " + value.trim().replace("\n", "\n> ");
    }

    private static String markdownTableCell(String value) {
        var normalized = fallback(value, "")
                .replace("|", "\\|")
                .replaceAll("\\R+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237).stripTrailing() + "...";
    }

    private static boolean meaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return !List.of("brak", "n/a", "none", "not applicable").contains(normalized);
    }

    private static String normalizeConfidence(String confidence) {
        if (!StringUtils.hasText(confidence)) {
            return "";
        }
        return confidence.trim().toLowerCase(Locale.ROOT);
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
