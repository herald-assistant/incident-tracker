package pl.mkn.tdw.features.configdriftviewer.job.api;

import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreement;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotation;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;
import java.util.Objects;

public record ConfigDriftViewerResult(
        ConfigDriftViewerStatus status,
        ConfigDriftViewerMode mode,
        ConfigDriftViewerDeterministicContext deterministicResult,
        ConfigDriftViewerDiffProjection configurationDiff,
        List<ConfigDriftViewerDiffAnnotation> configurationDiffAnnotations,
        ConfigDriftViewerAiSecondOpinion aiSecondOpinion,
        ConfigDriftViewerAgreement agreement,
        ConfigDriftViewerDeepContext deepAnalysis,
        List<String> visibilityLimits,
        String prompt,
        AnalysisAiUsage usage
) {

    public ConfigDriftViewerResult {
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
