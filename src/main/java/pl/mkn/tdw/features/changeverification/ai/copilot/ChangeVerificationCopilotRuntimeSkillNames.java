package pl.mkn.tdw.features.changeverification.ai.copilot;

import java.util.List;

public final class ChangeVerificationCopilotRuntimeSkillNames {

    public static final String ORCHESTRATOR = "change-verification-orchestrator";
    public static final String COMPLIANCE_CHECK = "change-verification-compliance-check";
    public static final String STORY_COMPLIANCE_SECTION = "change-verification-story-compliance-section";
    public static final String INSTRUCTION_COMPLIANCE_SECTION = "change-verification-instruction-compliance-section";
    public static final String INFERRED_CRITICAL_CHECKS_SECTION =
            "change-verification-inferred-critical-checks-section";
    public static final String WRITE_REPORT = "change-verification-write-report";

    private ChangeVerificationCopilotRuntimeSkillNames() {
    }

    public static List<String> featureSkillNames() {
        return List.of(
                ORCHESTRATOR,
                COMPLIANCE_CHECK,
                STORY_COMPLIANCE_SECTION,
                INSTRUCTION_COMPLIANCE_SECTION,
                INFERRED_CRITICAL_CHECKS_SECTION,
                WRITE_REPORT
        );
    }

}
