package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import java.util.List;

public final class ConfigDriftViewerCopilotRuntimeSkillNames {

    public static final String DEEP_REVIEW = "config-drift-viewer-deep-review";

    private ConfigDriftViewerCopilotRuntimeSkillNames() {
    }

    public static List<String> featureSkillNames() {
        return List.of(DEEP_REVIEW);
    }
}
