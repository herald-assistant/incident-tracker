package pl.mkn.tdw.aiplatform.copilot.tools.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CopilotReportSessionStore {

    private final ConcurrentMap<String, AnalysisReport> reportsById = new ConcurrentHashMap<>();

    public void register(AnalysisReport report) {
        reportsById.put(requireReportId(report), report);
    }

    public Optional<AnalysisReport> current(String reportId) {
        var normalizedReportId = normalize(reportId);
        if (!StringUtils.hasText(normalizedReportId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(reportsById.get(normalizedReportId));
    }

    public AnalysisReport replace(AnalysisReport report) {
        var reportId = requireReportId(report);
        return reportsById.compute(reportId, (key, current) -> {
            if (current == null) {
                throw noActiveReport(reportId);
            }
            return report;
        });
    }

    public AnalysisReport upsertSection(String reportId, AnalysisReportSection section) {
        var normalizedReportId = requireReportId(reportId);
        var sectionId = requireSectionId(section);
        return reportsById.compute(normalizedReportId, (key, current) -> {
            if (current == null) {
                throw noActiveReport(normalizedReportId);
            }

            var sections = new ArrayList<>(current.sections());
            var replaced = false;
            for (var index = 0; index < sections.size(); index++) {
                var currentSection = sections.get(index);
                if (currentSection != null && sectionId.equals(normalize(currentSection.id()))) {
                    sections.set(index, section);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                sections.add(section);
            }

            return new AnalysisReport(
                    current.reportId(),
                    current.header(),
                    current.subHeader(),
                    current.markdownSummary(),
                    sections,
                    current.meta()
            );
        });
    }

    public AnalysisReport updateMeta(String reportId, AnalysisReportMeta meta) {
        var normalizedReportId = requireReportId(reportId);
        if (meta == null) {
            throw new CopilotReportSessionException("Report meta must not be null.");
        }

        return reportsById.compute(normalizedReportId, (key, current) -> {
            if (current == null) {
                throw noActiveReport(normalizedReportId);
            }
            return new AnalysisReport(
                    current.reportId(),
                    current.header(),
                    current.subHeader(),
                    current.markdownSummary(),
                    current.sections(),
                    meta
            );
        });
    }

    public AnalysisReport updateHeader(
            String reportId,
            String header,
            String subHeader,
            String markdownSummary
    ) {
        var normalizedReportId = requireReportId(reportId);
        var normalizedHeader = normalize(header);
        if (!StringUtils.hasText(normalizedHeader)) {
            throw new CopilotReportSessionException("Report header must not be blank.");
        }

        return reportsById.compute(normalizedReportId, (key, current) -> {
            if (current == null) {
                throw noActiveReport(normalizedReportId);
            }
            return new AnalysisReport(
                    current.reportId(),
                    normalizedHeader,
                    keepCurrentWhenBlank(subHeader, current.subHeader()),
                    keepCurrentWhenBlank(markdownSummary, current.markdownSummary()),
                    current.sections(),
                    current.meta()
            );
        });
    }

    public Optional<AnalysisReport> unregister(String reportId) {
        var normalizedReportId = normalize(reportId);
        if (!StringUtils.hasText(normalizedReportId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(reportsById.remove(normalizedReportId));
    }

    private String requireReportId(AnalysisReport report) {
        if (report == null) {
            throw new CopilotReportSessionException("Report must not be null.");
        }
        return requireReportId(report.reportId());
    }

    private String requireReportId(String reportId) {
        var normalizedReportId = normalize(reportId);
        if (!StringUtils.hasText(normalizedReportId)) {
            throw new CopilotReportSessionException("Report id must not be blank.");
        }
        return normalizedReportId;
    }

    private String requireSectionId(AnalysisReportSection section) {
        if (section == null) {
            throw new CopilotReportSessionException("Report section must not be null.");
        }
        var sectionId = normalize(section.id());
        if (!StringUtils.hasText(sectionId)) {
            throw new CopilotReportSessionException("Report section id must not be blank.");
        }
        return sectionId;
    }

    private CopilotReportSessionException noActiveReport(String reportId) {
        return new CopilotReportSessionException("No active report registered for reportId=" + reportId + ".");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String keepCurrentWhenBlank(String candidate, String current) {
        var normalizedCandidate = normalize(candidate);
        return StringUtils.hasText(normalizedCandidate) ? normalizedCandidate : current;
    }
}
