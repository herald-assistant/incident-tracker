package pl.mkn.tdw.features.runtimeconfigurationverification.ai.report;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.List;

public final class RuntimeConfigurationReportSectionIds {

    public static final String VERIFICATION_SUMMARY = "verification-summary";
    public static final String DETERMINISTIC_DIFFERENCES = "deterministic-differences";
    public static final String DETERMINISTIC_FINDINGS = "deterministic-findings";
    public static final String AI_SECOND_OPINION = "ai-second-opinion";
    public static final String RECOMMENDED_HUMAN_CHECKS = "recommended-human-checks";
    public static final String AFFECTED_SYSTEMS_AND_CONTEXT = "affected-systems-and-context";
    public static final String FUNCTIONAL_IMPACT_AND_CODE_GROUNDING = "functional-impact-and-code-grounding";
    public static final String OWNERSHIP_AND_HANDOFF = "ownership-and-handoff";
    public static final String VISIBILITY_AND_GAPS = "visibility-and-gaps";

    private RuntimeConfigurationReportSectionIds() {
    }

    public static List<String> aiWritable(RuntimeConfigurationVerificationMode mode) {
        return mode == RuntimeConfigurationVerificationMode.DEEP
                ? List.of(AI_SECOND_OPINION, RECOMMENDED_HUMAN_CHECKS, FUNCTIONAL_IMPACT_AND_CODE_GROUNDING)
                : List.of(AI_SECOND_OPINION, RECOMMENDED_HUMAN_CHECKS);
    }
}
