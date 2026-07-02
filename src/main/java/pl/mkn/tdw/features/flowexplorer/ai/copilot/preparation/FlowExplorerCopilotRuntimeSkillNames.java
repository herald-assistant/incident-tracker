package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.List;

final class FlowExplorerCopilotRuntimeSkillNames {

    static final String STARTER_SKILL_NAME = "flow-explorer-orchestrator";
    static final String CODE_GROUNDING_SKILL_NAME = "flow-explorer-code-grounding";
    static final String OPERATIONAL_GROUNDING_SKILL_NAME = "flow-explorer-operational-grounding";
    static final String PERSISTENCE_SECTION_SKILL_NAME = "flow-explorer-map-persistence-section";
    static final String INTEGRATIONS_SECTION_SKILL_NAME = "flow-explorer-map-integrations-section";
    static final String WRITE_REPORT_SKILL_NAME = "flow-explorer-write-report";
    static final String FOLLOW_UP_CHAT_SKILL_NAME = "flow-explorer-follow-up-chat";
    static final String DEEP_DISCOVERY_SKILL_NAME = "flow-explorer-deep-discovery";
    static final String TEST_SCENARIOS_SKILL_NAME = "flow-explorer-test-scenario-design";
    static final String RISK_DETECTION_SKILL_NAME = "flow-explorer-risk-assessment";

    static List<String> initialSkillNames(FlowExplorerAnalysisGoal goal) {
        return initialSkillNames(goal, null);
    }

    static List<String> initialSkillNames(
            FlowExplorerAnalysisGoal goal,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        var skillNames = new java.util.ArrayList<>(List.of(
                STARTER_SKILL_NAME,
                CODE_GROUNDING_SKILL_NAME,
                OPERATIONAL_GROUNDING_SKILL_NAME
        ));
        addSectionSkillNames(skillNames, sectionModes);
        skillNames.add(WRITE_REPORT_SKILL_NAME);
        if (goal == FlowExplorerAnalysisGoal.DEEP_DISCOVERY) {
            skillNames.add(DEEP_DISCOVERY_SKILL_NAME);
        }
        if (goal == FlowExplorerAnalysisGoal.TEST_SCENARIOS) {
            skillNames.add(TEST_SCENARIOS_SKILL_NAME);
        }
        if (goal == FlowExplorerAnalysisGoal.RISK_DETECTION) {
            skillNames.add(RISK_DETECTION_SKILL_NAME);
        }
        return List.copyOf(skillNames);
    }

    static List<String> allSkillNames() {
        return List.of(
                STARTER_SKILL_NAME,
                CODE_GROUNDING_SKILL_NAME,
                OPERATIONAL_GROUNDING_SKILL_NAME,
                PERSISTENCE_SECTION_SKILL_NAME,
                INTEGRATIONS_SECTION_SKILL_NAME,
                WRITE_REPORT_SKILL_NAME,
                FOLLOW_UP_CHAT_SKILL_NAME,
                DEEP_DISCOVERY_SKILL_NAME,
                TEST_SCENARIOS_SKILL_NAME,
                RISK_DETECTION_SKILL_NAME
        );
    }

    static List<String> followUpSkillNames() {
        return followUpSkillNames(null);
    }

    static List<String> followUpSkillNames(List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        var skillNames = new java.util.ArrayList<>(List.of(
                FOLLOW_UP_CHAT_SKILL_NAME,
                CODE_GROUNDING_SKILL_NAME,
                OPERATIONAL_GROUNDING_SKILL_NAME
        ));
        addSectionSkillNames(skillNames, sectionModes);
        return List.copyOf(skillNames);
    }

    private static void addSectionSkillNames(
            java.util.ArrayList<String> skillNames,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        if (sectionModes == null || sectionModes.isEmpty()) {
            skillNames.add(PERSISTENCE_SECTION_SKILL_NAME);
            skillNames.add(INTEGRATIONS_SECTION_SKILL_NAME);
            return;
        }
        var activeSectionIds = FlowExplorerResultSectionModeResolver.activeOnly(sectionModes).stream()
                .map(FlowExplorerResultSectionModeAssignment::id)
                .toList();
        if (activeSectionIds.contains(FlowExplorerResultSectionId.PERSISTENCE)) {
            skillNames.add(PERSISTENCE_SECTION_SKILL_NAME);
        }
        if (activeSectionIds.contains(FlowExplorerResultSectionId.INTEGRATIONS)) {
            skillNames.add(INTEGRATIONS_SECTION_SKILL_NAME);
        }
    }

    private FlowExplorerCopilotRuntimeSkillNames() {
    }
}
