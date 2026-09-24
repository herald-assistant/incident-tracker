package pl.mkn.tdw.features.incidentanalysis.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.report.CopilotIncidentReportMapper;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentReportSectionIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class IncidentFollowUpReportProjection {
    private final CopilotIncidentReportMapper mapper;

    public AnalysisReport normalize(AnalysisResultResponse result, AnalysisReport current, String analysisId) {
        if (mapper.tryMap(current, result.prompt(), result.usage(), null).isPresent()) return current;
        var references = new ArrayList<AnalysisReportReference>();
        addReference(references, "process", result.affectedProcess());
        addReference(references, "boundedContext", result.affectedBoundedContext());
        addReference(references, "team", result.affectedTeam());
        var meta = new AnalysisReportMeta(references, result.visibilityLimits(), List.of(), List.of(),
                result.confidence(), List.of());
        return new AnalysisReport(current != null ? current.reportId() : "incident-report-" + analysisId,
                value(result.detectedProblem()), current != null ? current.subHeader() : result.correlationId(),
                value(result.detectedProblem()), List.of(
                new AnalysisReportSection(CopilotIncidentReportSectionIds.FUNCTIONAL_ANALYSIS,
                        "Functional analysis", 1, value(result.functionalAnalysis()), AnalysisReportMeta.empty()),
                new AnalysisReportSection(CopilotIncidentReportSectionIds.TECHNICAL_HANDOFF,
                        "Technical handoff", 2, value(result.technicalAnalysis()), AnalysisReportMeta.empty())), meta);
    }

    private void addReference(List<AnalysisReportReference> references, String type, String value) {
        if (value != null && !value.isBlank()) references.add(new AnalysisReportReference(type, value, null, null));
    }

    private String value(String value) { return value != null && !value.isBlank() ? value : "Nie ustalono."; }

    public AnalysisResultResponse project(AnalysisResultResponse previous, AnalysisReport current,
                                          AnalysisReport candidate) {
        if (Objects.equals(current, candidate)) return previous;
        if (candidate == null || current == null || !Objects.equals(current.reportId(), candidate.reportId())) {
            throw new IllegalStateException("Incident follow-up returned an invalid report identity.");
        }
        var mapped = mapper.tryMap(candidate, previous.prompt(), previous.usage(), null)
                .orElseThrow(() -> new IllegalStateException("Incident follow-up report is incomplete."));
        return new AnalysisResultResponse(previous.status(), previous.correlationId(), previous.environment(),
                previous.gitLabBranch(), mapped.detectedProblem(), mapped.affectedProcess(),
                mapped.affectedBoundedContext(), mapped.affectedTeam(), mapped.functionalAnalysis(),
                mapped.technicalAnalysis(), mapped.confidence(), mapped.visibilityLimits(),
                previous.prompt(), previous.usage());
    }
}
