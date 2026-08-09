package pl.mkn.tdw.features.configdriftviewer.ai;

import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiAssessment;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

public record ConfigDriftViewerAiRunResult(
        ConfigDriftViewerAiAssessment assessment,
        AnalysisAiUsage usage
) {
}
