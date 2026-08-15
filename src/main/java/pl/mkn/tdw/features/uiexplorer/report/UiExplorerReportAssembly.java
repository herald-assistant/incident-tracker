package pl.mkn.tdw.features.uiexplorer.report;

import pl.mkn.tdw.shared.ai.report.AnalysisReport;

public record UiExplorerReportAssembly(
        UiExplorerReportAssemblyStatus status,
        AnalysisReport report,
        String code,
        String message
) {

    public static UiExplorerReportAssembly available(AnalysisReport report) {
        return new UiExplorerReportAssembly(UiExplorerReportAssemblyStatus.AVAILABLE, report, null, null);
    }

    public static UiExplorerReportAssembly unavailable(String code, String message) {
        return new UiExplorerReportAssembly(UiExplorerReportAssemblyStatus.UNAVAILABLE, null, code, message);
    }
}

