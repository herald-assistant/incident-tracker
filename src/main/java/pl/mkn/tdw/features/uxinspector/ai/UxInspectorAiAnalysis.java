package pl.mkn.tdw.features.uxinspector.ai;

import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;

public record UxInspectorAiAnalysis(
        UxInspectorAiAnalysisStatus status,
        UxInspectorResultResponse result,
        AnalysisReport report,
        AnalysisAiUsage usage,
        String sessionId,
        List<String> limitations
) {
    public UxInspectorAiAnalysis { limitations = limitations != null ? List.copyOf(limitations) : List.of(); }
}

