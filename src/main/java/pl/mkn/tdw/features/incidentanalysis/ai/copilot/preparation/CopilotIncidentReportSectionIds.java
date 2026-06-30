package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import java.util.List;

public final class CopilotIncidentReportSectionIds {

    public static final String FUNCTIONAL_ANALYSIS = "FUNCTIONAL_ANALYSIS";
    public static final String TECHNICAL_HANDOFF = "TECHNICAL_HANDOFF";
    public static final List<String> INITIAL_ALLOWED_SECTION_IDS = List.of(
            FUNCTIONAL_ANALYSIS,
            TECHNICAL_HANDOFF
    );

    private CopilotIncidentReportSectionIds() {
    }
}
