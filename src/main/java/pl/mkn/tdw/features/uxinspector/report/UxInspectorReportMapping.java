package pl.mkn.tdw.features.uxinspector.report;

import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;

public record UxInspectorReportMapping(UxInspectorResultResponse result, AnalysisReport report,
                                       boolean complete, List<String> limitations) {
    public UxInspectorReportMapping {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}

