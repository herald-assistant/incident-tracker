package pl.mkn.tdw.features.uiexplorer.report;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;

public record UiExplorerReportMapping(
        UiExplorerResultResponse result,
        AnalysisReport report,
        boolean complete,
        List<String> limitations
) {
    public UiExplorerReportMapping {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
