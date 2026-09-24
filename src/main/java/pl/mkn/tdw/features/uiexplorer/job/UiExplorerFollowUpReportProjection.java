package pl.mkn.tdw.features.uiexplorer.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerReportMapper;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerReportMapping;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UiExplorerFollowUpReportProjection {
    private final UiExplorerReportMapper mapper;

    public UiExplorerReportMapping project(AnalysisReport current, AnalysisReport candidate,
            UiExplorerJobStartRequest request, UiExplorerScreenReachabilityContext context,
            AnalysisAiUsage usage, List<AnalysisEvidenceSection> evidence) {
        if (Objects.equals(current, candidate)) return null;
        if (candidate == null || current == null || !Objects.equals(current.reportId(), candidate.reportId())) {
            throw new IllegalStateException("UI Explorer follow-up returned an invalid report identity.");
        }
        var mapping = mapper.map(candidate, request, context, fetchedPaths(evidence), usage);
        if (mapping.result() == null || mapping.report() == null) {
            throw new IllegalStateException("UI Explorer follow-up report failed validation.");
        }
        return mapping;
    }

    private Set<String> fetchedPaths(List<AnalysisEvidenceSection> evidence) {
        var paths = new LinkedHashSet<String>();
        for (var section : evidence != null ? evidence : List.<AnalysisEvidenceSection>of()) {
            if (section == null || !"gitlab".equals(section.provider())
                    || !"tool-fetched-code".equals(section.category())) continue;
            section.items().stream().flatMap(item -> item.attributes().stream())
                    .filter(attribute -> "filePath".equals(attribute.name()))
                    .map(attribute -> attribute.value())
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(paths::add);
        }
        return Set.copyOf(paths);
    }
}
