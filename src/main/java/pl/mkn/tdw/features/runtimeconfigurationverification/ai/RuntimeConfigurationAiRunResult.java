package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiAssessment;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record RuntimeConfigurationAiRunResult(
        RuntimeConfigurationAiAssessment assessment,
        AnalysisAiUsage usage
) {
}
