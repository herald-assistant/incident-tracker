package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import java.util.List;

final class CopilotIncidentRuntimeSkillNames {

    static final String STARTER_SKILL_NAME = "incident-analysis-orchestrator";

    static final List<String> DIAGNOSTIC_SKILL_NAMES = List.of(
            "incident-operational-context-tools",
            "incident-analysis-gitlab-tools",
            "incident-data-diagnostics"
    );

    static final List<String> RESULT_SKILL_NAMES = List.of(
            "incident-functional-analysis",
            "incident-technical-handoff"
    );

    static List<String> allSkillNames() {
        var skillNames = new java.util.ArrayList<String>();
        skillNames.add(STARTER_SKILL_NAME);
        skillNames.addAll(DIAGNOSTIC_SKILL_NAMES);
        skillNames.addAll(RESULT_SKILL_NAMES);
        return List.copyOf(skillNames);
    }

    private CopilotIncidentRuntimeSkillNames() {
    }
}
