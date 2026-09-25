package pl.mkn.tdw.shared.ai.report;

import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class AnalysisReportManualEditor {
    private static final int MAX_MARKDOWN_BYTES = 1_048_576;

    private AnalysisReportManualEditor() {
    }

    public static AnalysisReport apply(AnalysisReport current, AnalysisReportEditRequest request) {
        if (current == null) {
            throw error("REPORT_EDIT_UNAVAILABLE", UserFacingErrorType.CONFLICT, "Report is unavailable for editing.");
        }
        if (request == null || request.expectedRevisionSha256() == null
                || !current.revisionSha256().equalsIgnoreCase(request.expectedRevisionSha256().trim())) {
            throw error("REPORT_EDIT_STALE", UserFacingErrorType.CONFLICT,
                    "Report changed since editing started. Reload the result before saving.");
        }
        var changed = new ArrayList<String>();
        var sections = new ArrayList<>(current.sections());
        var seen = new HashSet<String>();
        for (var edit : request.sections()) {
            if (edit == null || edit.sectionId() == null || !seen.add(edit.sectionId())) {
                throw error("REPORT_EDIT_INVALID", UserFacingErrorType.BAD_REQUEST, "Section id is missing or repeated.");
            }
            var index = -1;
            for (var i = 0; i < sections.size(); i++) {
                if (edit.sectionId().equals(sections.get(i).id())) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw error("REPORT_EDIT_INVALID", UserFacingErrorType.BAD_REQUEST, "Unknown report section.");
            }
            requireMarkdown(edit.markdown());
            var previous = sections.get(index);
            if (!previous.markdown().equals(edit.markdown())) {
                sections.set(index, new AnalysisReportSection(previous.id(), previous.title(), previous.order(),
                        edit.markdown(), previous.meta()));
                changed.add(previous.id());
            }
        }
        var summary = current.markdownSummary();
        if (request.markdownSummary() != null) {
            requireMarkdown(request.markdownSummary());
            if (!request.markdownSummary().equals(summary)) {
                summary = request.markdownSummary();
                changed.add("markdownSummary");
            }
        }
        if (changed.isEmpty()) {
            throw error("REPORT_EDIT_UNCHANGED", UserFacingErrorType.BAD_REQUEST, "No report text was changed.");
        }
        var revision = current.manualEdit() != null ? current.manualEdit().revision() + 1 : 1;
        var allChangedParts = new java.util.LinkedHashSet<String>();
        if (current.manualEdit() != null) allChangedParts.addAll(current.manualEdit().changedParts());
        allChangedParts.addAll(changed);
        return new AnalysisReport(current.reportId(), current.header(), current.subHeader(), summary,
                List.copyOf(sections), current.meta(),
                new AnalysisReportManualEdit(revision, Instant.now(), List.copyOf(allChangedParts)));
    }

    private static void requireMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw error("REPORT_EDIT_INVALID", UserFacingErrorType.BAD_REQUEST,
                    "Report Markdown must not be empty.");
        }
        if (markdown.getBytes(StandardCharsets.UTF_8).length > MAX_MARKDOWN_BYTES) {
            throw error("REPORT_EDIT_TOO_LARGE", UserFacingErrorType.BAD_REQUEST,
                    "Report Markdown exceeds the 1 MB section limit.");
        }
    }

    private static AnalysisReportEditException error(String code, UserFacingErrorType type, String message) {
        return new AnalysisReportEditException(code, type, message);
    }
}
