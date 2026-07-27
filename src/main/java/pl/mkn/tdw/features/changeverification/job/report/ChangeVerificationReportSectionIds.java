package pl.mkn.tdw.features.changeverification.job.report;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;

import java.util.ArrayList;
import java.util.List;

public final class ChangeVerificationReportSectionIds {

    public static final String STORY_COMPLIANCE = "STORY_COMPLIANCE";
    public static final String INSTRUCTION_COMPLIANCE = "INSTRUCTION_COMPLIANCE";
    public static final String SMOKE_PACK = "SMOKE_PACK";

    private ChangeVerificationReportSectionIds() {
    }

    public static List<String> activeComplianceSectionIds(ChangeVerificationJobStartRequest request) {
        var sectionIds = new ArrayList<String>();
        if (request != null && request.checkStoryCompliance()) {
            sectionIds.add(STORY_COMPLIANCE);
        }
        if (request != null && request.checkInstructionCompliance()) {
            sectionIds.add(INSTRUCTION_COMPLIANCE);
        }
        return List.copyOf(sectionIds);
    }
}
