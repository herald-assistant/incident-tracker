package pl.mkn.tdw.features.changeverification.ai.copilot;

import java.util.List;

public final class ChangeVerificationCopilotRuntimeSkillNames {

    public static final String ORCHESTRATOR = "change-verification-orchestrator";
    public static final String COMPLIANCE_CHECK = "change-verification-compliance-check";

    private ChangeVerificationCopilotRuntimeSkillNames() {
    }

    public static List<String> featureSkillNames() {
        return List.of(
                ORCHESTRATOR,
                COMPLIANCE_CHECK
        );
    }

}
