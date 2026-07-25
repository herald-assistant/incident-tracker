package pl.mkn.tdw.features.changeverification.ai.copilot;

import java.util.List;

public final class ChangeVerificationCopilotRuntimeSkillNames {

    public static final String COMPLIANCE_CHECK = "change-verification-compliance-check";
    public static final String SMOKE_PACK_DESIGN = "change-verification-smoke-pack-design";

    private ChangeVerificationCopilotRuntimeSkillNames() {
    }

    public static List<String> initialSkillNames() {
        return List.of(COMPLIANCE_CHECK);
    }

    public static List<String> smokePackSkillNames() {
        return List.of(SMOKE_PACK_DESIGN);
    }
}
