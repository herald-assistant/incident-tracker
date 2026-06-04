package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import java.util.List;

final class CopilotIncidentRuntimeSkillNames {

    static final List<String> PREFERRED_SKILL_NAMES = List.of(
            "incident-analysis-core",
            "incident-functional-analysis",
            "incident-technical-handoff",
            "incident-operational-context-tools",
            "incident-analysis-gitlab-tools",
            "incident-data-diagnostics"
    );

    private CopilotIncidentRuntimeSkillNames() {
    }
}
