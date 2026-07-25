package pl.mkn.tdw.features.changeverification.job.api;

import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
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
        String preparedPrompt,
        ChangeVerificationResultResponse result
) {

    public ChangeVerificationJobStateSnapshot {
        modes = modes != null ? List.copyOf(modes) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
    }
}
