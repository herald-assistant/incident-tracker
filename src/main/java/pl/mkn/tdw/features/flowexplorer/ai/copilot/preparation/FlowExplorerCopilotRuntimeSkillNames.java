package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

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

    static List<String> featureSkillNames() {
        return List.of(
                STARTER_SKILL_NAME,
                CODE_GROUNDING_SKILL_NAME,
                OPERATIONAL_GROUNDING_SKILL_NAME,
                PERSISTENCE_SECTION_SKILL_NAME,
                INTEGRATIONS_SECTION_SKILL_NAME,
                WRITE_REPORT_SKILL_NAME,
                FOLLOW_UP_CHAT_SKILL_NAME,
                DEEP_DISCOVERY_SKILL_NAME
        );
    }

    private FlowExplorerCopilotRuntimeSkillNames() {
    }
}
