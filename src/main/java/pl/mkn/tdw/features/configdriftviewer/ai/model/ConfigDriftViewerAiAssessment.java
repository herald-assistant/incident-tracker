package pl.mkn.tdw.features.configdriftviewer.ai.model;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record ConfigDriftViewerAiAssessment(
        ConfigDriftViewerAiSecondOpinion aiSecondOpinion,
        ConfigDriftViewerAgreement agreement,
        ConfigDriftViewerStatus combinedStatus,
        AnalysisReport report
) {
}
