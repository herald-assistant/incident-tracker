package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.List;

public final class RuntimeConfigurationCopilotRuntimeSkillNames {

    public static final String BASIC_REVIEW = "runtime-configuration-basic-review";
    public static final String DEEP_REVIEW = "runtime-configuration-deep-review";

    private RuntimeConfigurationCopilotRuntimeSkillNames() {
    }

    public static List<String> forMode(RuntimeConfigurationVerificationMode mode) {
        return mode == RuntimeConfigurationVerificationMode.DEEP
                ? List.of(DEEP_REVIEW)
                : List.of(BASIC_REVIEW);
    }
}
