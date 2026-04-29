package pl.mkn.incidenttracker.analysis.flow;

import pl.mkn.incidenttracker.analysis.ai.AnalysisAiUsage;

public record AnalysisResultResponse(
        String status,
        String correlationId,
        String environment,
        String gitLabBranch,
        String summary,
        String detectedProblem,
        String recommendedAction,
        String rationale,
        String affectedFunction,
        String affectedProcess,
        String affectedBoundedContext,
        String affectedTeam,
        String prompt,
        AnalysisAiUsage usage
) {

    public AnalysisResultResponse(
            String status,
            String correlationId,
            String environment,
            String gitLabBranch,
            String summary,
            String detectedProblem,
            String recommendedAction,
            String rationale,
            String affectedFunction,
            String affectedProcess,
            String affectedBoundedContext,
            String affectedTeam,
            String prompt
    ) {
        this(
                status,
                correlationId,
                environment,
                gitLabBranch,
                summary,
                detectedProblem,
                recommendedAction,
                rationale,
                affectedFunction,
                affectedProcess,
                affectedBoundedContext,
                affectedTeam,
                prompt,
                null
        );
    }
}
