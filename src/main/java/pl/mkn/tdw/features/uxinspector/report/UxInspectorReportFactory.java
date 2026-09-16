package pl.mkn.tdw.features.uxinspector.report;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

@Component
public class UxInspectorReportFactory {
    public static final String SECTION_ID = "answer";

    public AnalysisReport create(String reportId, UxInspectorTargetContext context) {
        return new AnalysisReport(reportId, "UX Inspector", context.view().label(), "",
                List.of(new AnalysisReportSection(SECTION_ID, "Odpowiedz", 1, "", AnalysisReportMeta.empty())),
                AnalysisReportMeta.empty());
    }
}

