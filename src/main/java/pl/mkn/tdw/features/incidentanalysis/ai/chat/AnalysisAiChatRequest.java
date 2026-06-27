package pl.mkn.tdw.features.incidentanalysis.ai.chat;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;

public record AnalysisAiChatRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        AnalysisAiChatAnalysisSnapshot analysisResult,
        List<AnalysisAiChatTurn> history,
        String message,
        String copilotSessionId,
        AnalysisAiOptions options,
        AnalysisAiAuthRef authRef
) {

    public AnalysisAiChatRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        history = history != null ? List.copyOf(history) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
    }

    public AnalysisAiChatRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            AnalysisAiChatAnalysisSnapshot analysisResult,
            List<AnalysisAiChatTurn> history,
            String message,
            String copilotSessionId,
            AnalysisAiOptions options
    ) {
        this(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup,
                evidenceSections,
                toolEvidenceSections,
                analysisResult,
                history,
                message,
                copilotSessionId,
                options,
                AnalysisAiAuthRef.localToken(null)
        );
    }

    public AnalysisAiChatRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            AnalysisAiChatAnalysisSnapshot analysisResult,
            List<AnalysisAiChatTurn> history,
            String message,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef
    ) {
        this(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup,
                evidenceSections,
                toolEvidenceSections,
                analysisResult,
                history,
                message,
                null,
                options,
                authRef
        );
    }

    public AnalysisAiChatRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            AnalysisAiChatAnalysisSnapshot analysisResult,
            List<AnalysisAiChatTurn> history,
            String message,
            AnalysisAiOptions options
    ) {
        this(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup,
                evidenceSections,
                toolEvidenceSections,
                analysisResult,
                history,
                message,
                null,
                options,
                AnalysisAiAuthRef.localToken(null)
        );
    }
}
