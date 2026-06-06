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

    private CopilotIncidentRuntimeSkillNames() {
    }
}
