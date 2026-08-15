package pl.mkn.tdw.features.uiexplorer.ai;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;

public record UiExplorerAiAnalysis(
        UiExplorerAiAnalysisStatus status,
        UiExplorerResultResponse result,
        AnalysisAiUsage usage,
        String preparedPrompt,
        String sessionId,
        AnalysisReport report,
        List<String> limitations
) {

    public UiExplorerAiAnalysis {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
