package pl.mkn.tdw.features.runtimeconfigurationverification.ai.model;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record RuntimeConfigurationAiAssessment(
        RuntimeConfigurationAiSecondOpinion aiSecondOpinion,
        RuntimeConfigurationAgreement agreement,
        RuntimeConfigurationVerificationStatus combinedStatus,
        AnalysisReport report
) {
}
