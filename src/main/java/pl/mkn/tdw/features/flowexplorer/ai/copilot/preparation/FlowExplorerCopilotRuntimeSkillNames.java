package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;

import java.util.List;

final class FlowExplorerCopilotRuntimeSkillNames {

    static final String STARTER_SKILL_NAME = "flow-explorer-orchestrator";
    static final String GITLAB_TOOLS_SKILL_NAME = "flow-explorer-gitlab-tools";
    static final String OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME = "flow-explorer-operational-context-tools";
    static final String RESULT_CONTRACT_SKILL_NAME = "flow-explorer-result-contract";
    static final String FOLLOW_UP_CHAT_SKILL_NAME = "flow-explorer-follow-up-chat";
    static final String DEEP_DISCOVERY_SKILL_NAME = "flow-explorer-goal-deep-discovery";
    static final String TEST_SCENARIOS_SKILL_NAME = "flow-explorer-goal-test-scenarios";
    static final String RISK_DETECTION_SKILL_NAME = "flow-explorer-goal-risk-detection";

    static List<String> initialSkillNames(FlowExplorerAnalysisGoal goal) {
        var skillNames = new java.util.ArrayList<>(List.of(
                STARTER_SKILL_NAME,
                GITLAB_TOOLS_SKILL_NAME,
                OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME,
                RESULT_CONTRACT_SKILL_NAME
        ));
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
                GITLAB_TOOLS_SKILL_NAME,
                OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME,
                RESULT_CONTRACT_SKILL_NAME,
                FOLLOW_UP_CHAT_SKILL_NAME,
                DEEP_DISCOVERY_SKILL_NAME,
                TEST_SCENARIOS_SKILL_NAME,
                RISK_DETECTION_SKILL_NAME
        );
    }

    static List<String> followUpSkillNames() {
        return List.of(
                FOLLOW_UP_CHAT_SKILL_NAME,
                GITLAB_TOOLS_SKILL_NAME,
                OPERATIONAL_CONTEXT_TOOLS_SKILL_NAME
        );
    }

    private FlowExplorerCopilotRuntimeSkillNames() {
    }
}
