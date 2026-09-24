package pl.mkn.tdw.features.flowexplorer.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportMapper;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportSectionIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FlowExplorerFollowUpReportProjection {
    private final FlowExplorerReportMapper mapper;

    public AnalysisReport normalize(FlowExplorerResultResponse result, AnalysisReport current,
            List<FlowExplorerResultSectionModeAssignment> sectionModes, String jobId) {
        if (mapper.tryMap(current, result.goal(), sectionModes).isPresent()) return current;
        var response = result.aiResponse();
        var sections = new ArrayList<AnalysisReportSection>();
        sections.add(new AnalysisReportSection(FlowExplorerReportSectionIds.OVERVIEW, "Overview", 0,
                value(response.overview().markdown()), meta(response.overview().sourceRefs(), List.of(), List.of(),
                        response.overview().confidence())));
        var index = 1;
        for (var section : response.sections()) {
            if (section == null || section.id() == null) continue;
            sections.add(new AnalysisReportSection(section.id().name(), section.title(), index++,
                    value(section.markdown()), meta(section.sourceRefs(), section.visibilityLimits(),
                    section.openQuestions(), null)));
        }
        return new AnalysisReport(current != null ? current.reportId() : "flow-explorer-report-" + jobId,
                current != null ? current.header() : "Flow Explorer", current != null ? current.subHeader() : result.systemId(),
                response.overview().markdown(), sections,
                meta(response.sourceReferences(), response.globalVisibilityLimits(),
                        response.globalOpenQuestions(), response.confidence()));
    }

    private AnalysisReportMeta meta(List<String> refs, List<String> limits, List<String> questions, String confidence) {
        return new AnalysisReportMeta(refs.stream().map(ref -> new AnalysisReportReference("source", ref, ref, null)).toList(),
                limits, questions, List.of(), confidence, List.of());
    }

    private String value(String value) { return value != null && !value.isBlank() ? value : "Nie ustalono na podstawie dostepnych danych."; }

    public FlowExplorerResultResponse project(FlowExplorerResultResponse previous, AnalysisReport current,
            AnalysisReport candidate, List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        if (Objects.equals(current, candidate)) return previous;
        if (candidate == null || current == null || !Objects.equals(current.reportId(), candidate.reportId())) {
            throw new IllegalStateException("Flow Explorer follow-up returned an invalid report identity.");
        }
        var response = mapper.tryMap(candidate, previous.goal(), sectionModes)
                .orElseThrow(() -> new IllegalStateException("Flow Explorer follow-up report is incomplete."));
        return new FlowExplorerResultResponse(previous.status(), previous.systemId(), previous.endpointId(),
                previous.httpMethod(), previous.endpointPath(), previous.branch(), previous.goal(),
                previous.prompt(), response, previous.usage());
    }
}
