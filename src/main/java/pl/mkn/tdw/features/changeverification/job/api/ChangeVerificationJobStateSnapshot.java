package pl.mkn.tdw.features.changeverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record ChangeVerificationJobStateSnapshot(
        String jobId,
        String issueKey,
        String issueUrl,
        List<ChangeVerificationJobMode> modes,
        boolean checkStoryCompliance,
        boolean checkInstructionCompliance,
        String aiModel,
        String reasoningEffort,
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
        ChangeVerificationResultResponse result,
        AnalysisReport report
) {

    public ChangeVerificationJobStateSnapshot(
            String jobId,
            String issueKey,
            String issueUrl,
            List<ChangeVerificationJobMode> modes,
            boolean checkStoryCompliance,
            boolean checkInstructionCompliance,
            String aiModel,
            String reasoningEffort,
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
            ChangeVerificationResultResponse result
    ) {
        this(
                jobId,
                issueKey,
                issueUrl,
                modes,
                checkStoryCompliance,
                checkInstructionCompliance,
                aiModel,
                reasoningEffort,
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                preparedPrompt,
                result,
                null
        );
    }

    public ChangeVerificationJobStateSnapshot {
        modes = modes != null ? List.copyOf(modes) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
    }
}
