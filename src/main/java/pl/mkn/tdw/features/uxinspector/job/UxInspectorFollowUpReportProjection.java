package pl.mkn.tdw.features.uxinspector.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapping;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class UxInspectorFollowUpReportProjection {
    private final UxInspectorReportMapper mapper;

    public UxInspectorReportMapping project(AnalysisReport current, AnalysisReport candidate,
            UxInspectorCapture capture, UxInspectorTargetContext context, AnalysisAiUsage usage) {
        if (Objects.equals(current, candidate)) return null;
        if (candidate == null || current == null || !Objects.equals(current.reportId(), candidate.reportId())) {
            throw new IllegalStateException("UX Inspector follow-up returned an invalid report identity.");
        }
        var mapping = mapper.map(candidate, capture, context, usage);
        if (mapping.result() == null || mapping.report() == null) {
            throw new IllegalStateException("UX Inspector follow-up report failed validation.");
        }
        return mapping;
    }
}
