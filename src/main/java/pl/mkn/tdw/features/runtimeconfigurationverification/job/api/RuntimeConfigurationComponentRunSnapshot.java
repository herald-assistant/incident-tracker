package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record RuntimeConfigurationComponentRunSnapshot(
        String componentRunId,
        String systemId,
        String systemLabel,
        String configurationDirectory,
        String status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        String preparedPrompt,
        RuntimeConfigurationVerificationResult result,
        AnalysisReport report
) {

    public RuntimeConfigurationComponentRunSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
    }
}
