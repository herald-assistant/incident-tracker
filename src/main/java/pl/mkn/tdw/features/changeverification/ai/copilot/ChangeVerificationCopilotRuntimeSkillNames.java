package pl.mkn.tdw.features.changeverification.ai.copilot;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;

import java.util.ArrayList;
import java.util.List;

public final class ChangeVerificationCopilotRuntimeSkillNames {

    public static final String ORCHESTRATOR = "change-verification-orchestrator";
    public static final String COMPLIANCE_CHECK = "change-verification-compliance-check";
    public static final String STORY_COMPLIANCE_SECTION = "change-verification-story-compliance-section";
    public static final String INSTRUCTION_COMPLIANCE_SECTION = "change-verification-instruction-compliance-section";
    public static final String WRITE_REPORT = "change-verification-write-report";
    public static final String SMOKE_PACK_DESIGN = "change-verification-smoke-pack-design";

    private ChangeVerificationCopilotRuntimeSkillNames() {
    }

    public static List<String> initialSkillNames() {
        return initialSkillNames(null);
    }

    public static List<String> initialSkillNames(ChangeVerificationJobStartRequest request) {
        var skillNames = new ArrayList<>(List.of(ORCHESTRATOR, COMPLIANCE_CHECK));
        if (request == null || request.checkStoryCompliance()) {
            skillNames.add(STORY_COMPLIANCE_SECTION);
        }
        if (request == null || request.checkInstructionCompliance()) {
            skillNames.add(INSTRUCTION_COMPLIANCE_SECTION);
        }
        skillNames.add(WRITE_REPORT);
        return List.copyOf(skillNames);
    }

    public static List<String> smokePackSkillNames() {
        return List.of(SMOKE_PACK_DESIGN);
    }
}
