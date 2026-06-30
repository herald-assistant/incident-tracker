package pl.mkn.tdw.features.flowexplorer.job.api;

import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record FlowExplorerJobStateSnapshot(
        String jobId,
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        FlowExplorerAnalysisGoal goal,
        List<FlowExplorerFocusArea> focusAreas,
        List<FlowExplorerResultSectionModeAssignment> sectionModes,
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
        FlowExplorerContextSnapshot contextSnapshot,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        List<AnalysisAiToolFeedback> toolFeedback,
        List<AnalysisChatMessageResponse> chatMessages,
        String preparedPrompt,
        FlowExplorerResultResponse result,
        AnalysisReport report
) {

    public FlowExplorerJobStateSnapshot {
        focusAreas = focusAreas != null ? List.copyOf(focusAreas) : List.of();
        sectionModes = sectionModes != null ? List.copyOf(sectionModes) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        toolFeedback = toolFeedback != null ? List.copyOf(toolFeedback) : List.of();
        chatMessages = chatMessages != null ? List.copyOf(chatMessages) : List.of();
    }

    public FlowExplorerJobStateSnapshot(
            String jobId,
            String systemId,
            String endpointId,
            String httpMethod,
            String endpointPath,
            String branch,
            FlowExplorerAnalysisGoal goal,
            List<FlowExplorerFocusArea> focusAreas,
            List<FlowExplorerResultSectionModeAssignment> sectionModes,
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
            FlowExplorerContextSnapshot contextSnapshot,
            List<AnalysisEvidenceSection> contextSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            List<AnalysisAiActivityEvent> aiActivityEvents,
            List<AnalysisAiToolFeedback> toolFeedback,
            List<AnalysisChatMessageResponse> chatMessages,
            String preparedPrompt,
            FlowExplorerResultResponse result
    ) {
        this(
                jobId,
                systemId,
                endpointId,
                httpMethod,
                endpointPath,
                branch,
                goal,
                focusAreas,
                sectionModes,
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
                contextSnapshot,
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                toolFeedback,
                chatMessages,
                preparedPrompt,
                result,
                null
        );
    }
}
