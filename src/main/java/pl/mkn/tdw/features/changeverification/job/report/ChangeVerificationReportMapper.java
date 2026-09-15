package pl.mkn.tdw.features.changeverification.job.report;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleOutcome;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleScope;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ChangeVerificationReportMapper {

    private ChangeVerificationReportMapper() {
    }

    public static AnalysisReport toReport(ChangeVerificationResultResponse result) {
        if (result == null || result.ruleLedger() == null) {
            return null;
        }
        var ledger = result.ruleLedger();
        var sections = new ArrayList<AnalysisReportSection>();
        sections.add(new AnalysisReportSection(
                ChangeVerificationReportSectionIds.RULE_LEDGER,
                "Source-defined rules",
                0,
                rulesMarkdown(ledger.rules()),
                meta(ledger.rules(), ledger.visibilityLimits().stream().map(limit -> limit.message()).toList())
        ));
        if (!ledger.additionalChecks().isEmpty()) {
            sections.add(new AnalysisReportSection(
                    ChangeVerificationReportSectionIds.ADDITIONAL_CHECKS,
                    "AI-suggested checks",
                    1,
                    rulesMarkdown(ledger.additionalChecks()),
                    meta(ledger.additionalChecks(), List.of())
            ));
        }
        var decision = ledger.decision();
        return new AnalysisReport(
                "change-verification-" + fallback(result.issueKey(), "result"),
                "Change Verification: " + fallback(result.issueKey(), result.issueUrl(), "change"),
                "Decision " + decision.status(),
                "Source rules: " + decision.totalRules()
                        + "; satisfied: " + decision.satisfied()
                        + "; not satisfied: " + decision.notSatisfied()
                        + "; not verified: " + decision.notVerified() + ".",
                sections,
                meta(ledger.rules(), ledger.visibilityLimits().stream().map(limit -> limit.message()).toList())
        );
    }

    private static String rulesMarkdown(List<ChangeVerificationRuleResultResponse> rules) {
        if (rules == null || rules.isEmpty()) {
            return "No rules were available for verification.";
        }
        var lines = new ArrayList<String>();
        ChangeVerificationRuleScope previousScope = null;
        for (var rule : rules) {
            if (rule.scope() != previousScope) {
                lines.add("## " + scopeLabel(rule.scope()));
                previousScope = rule.scope();
            }
            lines.add("### [" + rule.outcome() + "] " + rule.source().quote());
            lines.add("- Source: " + rule.source().label() + " (`" + rule.source().reference() + "`)");
            lines.add("- Interpretation: " + rule.normalizedRule());
            lines.add("- Conclusion: " + rule.conclusion());
            lines.add("- Release impact: `" + rule.releaseImpact() + "`");
            rule.evidence().forEach(evidence -> lines.add(
                    "- Evidence: " + evidence.summary() + " (`" + evidence.reference() + "`)"
            ));
            rule.missingEvidence().forEach(gap -> lines.add("- Missing evidence: " + gap));
            if (StringUtils.hasText(rule.action())) {
                lines.add("- Action: " + rule.action().trim());
            }
            lines.add("");
        }
        return String.join("\n", lines).trim();
    }

    private static AnalysisReportMeta meta(
            List<ChangeVerificationRuleResultResponse> rules,
            List<String> visibilityLimits
    ) {
        var references = new ArrayList<AnalysisReportReference>();
        var gaps = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var rule : rules != null ? rules : List.<ChangeVerificationRuleResultResponse>of()) {
            references.add(new AnalysisReportReference(
                    rule.source().type().name(),
                    rule.source().label(),
                    rule.source().reference(),
                    rule.source().quote()
            ));
            rule.evidence().forEach(evidence -> references.add(new AnalysisReportReference(
                    "EVIDENCE",
                    evidence.summary(),
                    evidence.reference(),
                    rule.id()
            )));
            gaps.addAll(rule.missingEvidence());
            if (rule.outcome() != ChangeVerificationRuleOutcome.SATISFIED) {
                warnings.add(rule.source().quote() + ": " + rule.conclusion());
            }
        }
        return new AnalysisReportMeta(
                distinctReferences(references),
                distinct(visibilityLimits),
                List.of(),
                distinct(gaps),
                null,
                distinct(warnings)
        );
    }

    private static String scopeLabel(ChangeVerificationRuleScope scope) {
        return switch (scope) {
            case STORY -> "Story rules";
            case INSTRUCTION -> "Repository instructions";
            case ADDITIONAL -> "AI-suggested checks";
        };
    }

    private static List<AnalysisReportReference> distinctReferences(List<AnalysisReportReference> values) {
        var seen = new LinkedHashSet<String>();
        return values.stream()
                .filter(value -> seen.add(value.type() + "|" + value.label() + "|" + value.target()))
                .toList();
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList()));
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
