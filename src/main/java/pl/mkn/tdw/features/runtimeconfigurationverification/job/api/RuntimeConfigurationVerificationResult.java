package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreement;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;

public record RuntimeConfigurationVerificationResult(
        RuntimeConfigurationVerificationStatus status,
        RuntimeConfigurationVerificationMode mode,
        RuntimeConfigurationDeterministicContext deterministicResult,
        RuntimeConfigurationAiSecondOpinion aiSecondOpinion,
        RuntimeConfigurationAgreement agreement,
        RuntimeConfigurationDeepContext deepAnalysis,
        List<String> visibilityLimits,
        String prompt,
        AnalysisAiUsage usage
) {

    public RuntimeConfigurationVerificationResult {
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
