package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreement;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotation;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;
import java.util.Objects;

public record RuntimeConfigurationVerificationResult(
        RuntimeConfigurationVerificationStatus status,
        RuntimeConfigurationVerificationMode mode,
        RuntimeConfigurationDeterministicContext deterministicResult,
        RuntimeConfigurationDiffProjection configurationDiff,
        List<RuntimeConfigurationDiffAnnotation> configurationDiffAnnotations,
        RuntimeConfigurationAiSecondOpinion aiSecondOpinion,
        RuntimeConfigurationAgreement agreement,
        RuntimeConfigurationDeepContext deepAnalysis,
        List<String> visibilityLimits,
        String prompt,
        AnalysisAiUsage usage
) {

    public RuntimeConfigurationVerificationResult {
        deterministicResult = Objects.requireNonNull(
                deterministicResult,
                "deterministicResult is required"
        );
        configurationDiff = Objects.requireNonNull(configurationDiff, "configurationDiff is required");
        configurationDiffAnnotations = List.copyOf(Objects.requireNonNull(
                configurationDiffAnnotations,
                "configurationDiffAnnotations are required"
        ));
        visibilityLimits = List.copyOf(Objects.requireNonNull(
                visibilityLimits,
                "visibilityLimits are required"
        ));
    }
}
