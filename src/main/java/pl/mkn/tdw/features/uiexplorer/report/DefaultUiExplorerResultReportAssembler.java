package pl.mkn.tdw.features.uiexplorer.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class DefaultUiExplorerResultReportAssembler implements UiExplorerResultReportAssembler {

    @Override
    public UiExplorerReportAssembly assemble(String reportId, UiExplorerResultResponse result) {
        if (result == null) {
            return UiExplorerReportAssembly.unavailable(
                    "UI_EXPLORER_RESULT_NOT_AVAILABLE",
                    "UI Explorer result is not available before screen analysis completes."
            );
        }
        if (!StringUtils.hasText(reportId)) {
            throw new IllegalArgumentException("reportId must not be blank when UI Explorer result is available");
        }

        var sections = result.sections().stream()
                .filter(section -> section.sectionId() != null)
                .filter(section -> section.mode() != UiExplorerSectionMode.OFF)
                .sorted(java.util.Comparator.comparing(section -> section.sectionId().ordinal()))
                .map(this::reportSection)
                .toList();
        var meta = new AnalysisReportMeta(
                references(result.sections()),
                result.visibilityLimits(),
                result.unresolvedQuestions(),
                coverageGaps(result.sections()),
                result.overallConfidence() != null ? result.overallConfidence().name() : null,
                List.of()
        );
        var report = new AnalysisReport(
                reportId.trim(),
                "UI Explorer: " + screenLabel(result),
                sourceLabel(result),
                valueOrEmpty(result.functionalOverview()),
                sections,
                meta
        );
        return UiExplorerReportAssembly.available(report);
    }

    private AnalysisReportSection reportSection(UiExplorerResultSection section) {
        return new AnalysisReportSection(
                section.sectionId().name(),
                section.sectionId().label(),
                section.sectionId().ordinal(),
                valueOrEmpty(section.markdown()).trim(),
                new AnalysisReportMeta(
                        references(section),
                        section.visibilityLimits(),
                        section.openQuestions(),
                        section.coverage() == UiExplorerCoverageStatus.READY
                                ? List.of()
                                : List.of("Section coverage: " + coverageLabel(section)),
                        confidence(section),
                        List.of()
                )
        );
    }

    private List<AnalysisReportReference> references(List<UiExplorerResultSection> sections) {
        var references = new LinkedHashSet<AnalysisReportReference>();
        sections.forEach(section -> references.addAll(references(section)));
        return List.copyOf(references);
    }

    private List<AnalysisReportReference> references(UiExplorerResultSection section) {
        var references = new LinkedHashSet<UiExplorerSourceReference>();
        references.addAll(section.sourceReferences());
        return references.stream().map(this::reportReference).toList();
    }

    private AnalysisReportReference reportReference(UiExplorerSourceReference source) {
        var target = new StringBuilder();
        if (StringUtils.hasText(source.repository())) {
            target.append(source.repository().trim()).append(':');
        }
        if (StringUtils.hasText(source.path())) {
            target.append(source.path().trim());
        }
        if (source.startLine() != null) {
            target.append("#L").append(source.startLine());
            if (source.endLine() != null && !source.endLine().equals(source.startLine())) {
                target.append("-L").append(source.endLine());
            }
        }
        return new AnalysisReportReference(
                "source",
                StringUtils.hasText(source.symbol()) ? source.symbol().trim() : valueOrEmpty(source.path()),
                target.toString(),
                StringUtils.hasText(source.symbol()) ? "Source symbol " + source.symbol().trim() : "UI source"
        );
    }

    private List<String> coverageGaps(List<UiExplorerResultSection> sections) {
        return sections.stream()
                .filter(section -> section.coverage() != null)
                .filter(section -> section.coverage() != UiExplorerCoverageStatus.READY)
                .map(section -> section.sectionId().name() + ": " + section.coverage().name())
                .toList();
    }

    private String confidence(UiExplorerResultSection section) {
        return section.confidence() != null ? section.confidence().name() : null;
    }

    private String coverageLabel(UiExplorerResultSection section) {
        return section.coverage() != null ? section.coverage().name() : "UNKNOWN";
    }

    private String screenLabel(UiExplorerResultResponse result) {
        if (result.screen() != null && StringUtils.hasText(result.screen().label())) {
            return result.screen().label().trim();
        }
        if (result.screen() != null && StringUtils.hasText(result.screen().screenId())) {
            return result.screen().screenId().trim();
        }
        return "screen";
    }

    private String sourceLabel(UiExplorerResultResponse result) {
        if (result.sourceRevision() == null) {
            return "Source revision unavailable";
        }
        var branch = valueOrEmpty(result.sourceRevision().branch());
        var revision = valueOrEmpty(result.sourceRevision().revision());
        return StringUtils.hasText(revision) ? branch + " @ " + revision : branch;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
