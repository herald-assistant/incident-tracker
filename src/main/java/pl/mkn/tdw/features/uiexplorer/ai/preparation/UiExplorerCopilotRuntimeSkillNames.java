package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import java.util.List;

public final class UiExplorerCopilotRuntimeSkillNames {

    public static final String ORCHESTRATOR = "ui-explorer-orchestrator";
    public static final String SOURCE_GROUNDING = "ui-explorer-source-grounding";
    public static final String WRITE_REPORT = "ui-explorer-write-report";

    private UiExplorerCopilotRuntimeSkillNames() {
    }

    public static List<String> featureSkillNames() {
        return List.of(ORCHESTRATOR, SOURCE_GROUNDING, WRITE_REPORT);
    }
}
