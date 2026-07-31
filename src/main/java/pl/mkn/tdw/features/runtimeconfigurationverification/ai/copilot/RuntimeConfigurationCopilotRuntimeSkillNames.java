package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import java.util.List;

public final class RuntimeConfigurationCopilotRuntimeSkillNames {

    public static final String DEEP_REVIEW = "runtime-configuration-deep-review";

    private RuntimeConfigurationCopilotRuntimeSkillNames() {
    }

    public static List<String> deepReview() {
        return List.of(DEEP_REVIEW);
    }
}
