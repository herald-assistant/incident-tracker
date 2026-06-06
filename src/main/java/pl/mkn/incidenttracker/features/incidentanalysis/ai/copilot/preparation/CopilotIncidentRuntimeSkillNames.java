package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import java.util.List;

final class CopilotIncidentRuntimeSkillNames {

    static final String STARTER_SKILL_NAME = "incident-analysis-orchestrator";

    static final List<String> PREFERRED_SKILL_NAMES = List.of(
            STARTER_SKILL_NAME,
            "incident-functional-analysis",
            "incident-technical-handoff",
            "incident-operational-context-tools",
            "incident-analysis-gitlab-tools",
            "incident-data-diagnostics"
    );

    private CopilotIncidentRuntimeSkillNames() {
    }
}
