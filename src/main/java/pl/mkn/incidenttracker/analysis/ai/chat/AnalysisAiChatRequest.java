package pl.mkn.incidenttracker.analysis.ai.chat;

import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

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
        AnalysisAiOptions options
) {

    public AnalysisAiChatRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        history = history != null ? List.copyOf(history) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
    }
}
