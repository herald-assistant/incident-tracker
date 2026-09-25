package pl.mkn.tdw.aiplatform.copilot.tools.report;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CopilotReportTools {

    private static final String STATUS_OK = "ok";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_MISSING_REPORT = "missing_report";
    private static final int MAX_INLINE_SECTION_BYTES = 2_048;
    // JSON can expand a control character to six ASCII bytes (\\u00XX).
    private static final int CHUNK_BYTES = 1_024;

    private final CopilotReportSessionStore reportStore;

    @Tool(
            name = CopilotReportToolNames.GET_CURRENT,
            description = """
                    Returns a compact validation manifest of the current structured analysis report for this AI session.
                    The manifest confirms persisted headers, section ids, order, content lengths, SHA-256 digests,
                    metadata counts and structural completeness without repeating all section bodies. Pass sectionId
                    to read a small section body and metadata. For a large body, use report_read_section_chunk.
                    The active report is selected from hidden ToolContext. Do not provide reportId, analysisId,
                    correlationId, environment, gitLabGroup or gitLabBranch.
                    """
    )
    public CopilotReportToolResult getCurrentReport(
            @ToolParam(required = false, description = "Optional allowed section id whose full current content should be read.")
            String sectionId,
            @ToolParam(required = false, description = "Short Polish reason why the current report is needed.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }

        var report = reportStore.current(scope.reportId());
        if (report.isEmpty()) {
            return missingReport("No active report is registered for this reportId.", scope);
        }
        var requestedSectionId = normalize(sectionId);
        if (!StringUtils.hasText(requestedSectionId)) {
            return ok("Current report validation manifest returned.", scope, report.get(), List.of());
        }
        if (!scope.allowedSectionIds().contains(requestedSectionId)) {
            return rejected("Report section id is not allowed for this session.", scope, null);
        }
        return report.get().sections().stream()
                .filter(section -> section != null && requestedSectionId.equals(normalize(section.id())))
                .findFirst()
                .map(section -> new CopilotReportToolResult(STATUS_OK,
                        inlineSection(section) ? "Current report section returned."
                                : "Section exceeds inline tool output. Read it with report_read_section_chunk.",
                        scope.reportId(), scope.reportFeature(),
                        CopilotReportManifestFactory.create(report.get(), scope.allowedSectionIds()),
                        List.of(), scope.allowedSectionIds(), inlineSection(section) ? section : null))
                .orElseGet(() -> rejected("Report section does not exist.", scope, null));
    }

    @Tool(
            name = CopilotReportToolNames.READ_SECTION_CHUNK,
            description = """
                    Reads one bounded UTF-8 chunk of an allowed section or markdownSummary in the current report.
                    Start at chunkIndex 0 and follow nextChunkIndex until hasMore is false.
                    Use the SHA-256 digest to ensure all chunks came from the same revision.
                    Report identity and allowed section ids come from hidden ToolContext.
                    """
    )
    public CopilotReportSectionChunk readSectionChunk(
            @ToolParam(description = "Allowed report section id, or markdownSummary.") String sectionId,
            @ToolParam(description = "Zero-based chunk index; start at 0.") Integer chunkIndex,
            @ToolParam(required = false, description = "Short Polish reason for reading this section.") String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        var id = normalize(sectionId);
        if (!StringUtils.hasText(scope.reportId()) || reportStore.current(scope.reportId()).isEmpty()) {
            return new CopilotReportSectionChunk(STATUS_MISSING_REPORT, "No active report is registered.",
                    id, null, chunkIndex, null, false, null);
        }
        if (id == null || (!scope.allowedSectionIds().contains(id) && !"markdownSummary".equals(id))
                || chunkIndex == null || chunkIndex < 0) {
            return new CopilotReportSectionChunk(STATUS_REJECTED, "Section id or chunk index is not allowed.",
                    id, null, chunkIndex, null, false, null);
        }
        var report = reportStore.current(scope.reportId()).orElseThrow();
        if ("markdownSummary".equals(id)) {
            return chunkOf(id, report.markdownSummary(), chunkIndex);
        }
        var section = report.sections().stream()
                .filter(candidate -> candidate != null && id.equals(candidate.id()))
                .findFirst().orElse(null);
        if (section == null) {
            return new CopilotReportSectionChunk(STATUS_REJECTED, "Report section does not exist.",
                    id, null, chunkIndex, null, false, null);
        }
        return chunkOf(id, section.markdown(), chunkIndex);
    }

    private CopilotReportSectionChunk chunkOf(String id, String markdown, int chunkIndex) {
        var chunks = splitUtf8(markdown != null ? markdown : "");
        if (chunkIndex >= chunks.size()) {
            return new CopilotReportSectionChunk(STATUS_REJECTED, "Chunk index is outside this section.",
                    id, CopilotReportManifestFactory.markdownSha256(markdown), chunkIndex, null, false, null);
        }
        var more = chunkIndex + 1 < chunks.size();
        return new CopilotReportSectionChunk(STATUS_OK, "Report section chunk returned.", id,
                CopilotReportManifestFactory.markdownSha256(markdown), chunkIndex,
                more ? chunkIndex + 1 : null, more, chunks.get(chunkIndex));
    }

    private boolean inlineSection(AnalysisReportSection section) {
        return section.markdown() == null
                || section.markdown().getBytes(StandardCharsets.UTF_8).length <= MAX_INLINE_SECTION_BYTES;
    }

    private List<String> splitUtf8(String markdown) {
        var chunks = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        var bytes = 0;
        for (var offset = 0; offset < markdown.length();) {
            var point = markdown.codePointAt(offset);
            var value = new String(Character.toChars(point));
            var size = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > CHUNK_BYTES && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
                bytes = 0;
            }
            current.append(value);
            bytes += size;
            offset += Character.charCount(point);
        }
        chunks.add(current.toString());
        return chunks;
    }

    public CopilotReportToolResult getCurrentReport(String reason, ToolContext toolContext) {
        return getCurrentReport(null, reason, toolContext);
    }

    @Tool(
            name = CopilotReportToolNames.UPSERT_SECTION,
            description = """
                    Creates or replaces one section in the current structured analysis report.
                    Use this to save final or revised report content. The active reportId and allowed section ids
                    are taken from hidden ToolContext; never provide reportId as an argument.
                    """
    )
    public CopilotReportToolResult upsertSection(
            @ToolParam(description = "Canonical section id allowed for this feature, for example OVERVIEW or TECHNICAL_HANDOFF.")
            String id,
            @ToolParam(required = false, description = "Human-readable section title.")
            String title,
            @ToolParam(required = false, description = "Display order of the section.")
            Integer order,
            @ToolParam(description = "Markdown body of the section.")
            String markdown,
            @ToolParam(required = false, description = "Optional section-level metadata: references, visibilityLimits, openQuestions, gaps, confidence and warnings.")
            AnalysisReportMeta meta,
            @ToolParam(required = false, description = "Short Polish reason why this section is being written.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }

        var sectionId = normalize(id);
        if (!StringUtils.hasText(sectionId)) {
            return rejected("Report section id must not be blank.", scope, null);
        }
        if (scope.allowedSectionIds().isEmpty()) {
            return rejected("No allowed report section ids are available in hidden ToolContext.", scope, null);
        }
        if (!scope.allowedSectionIds().contains(sectionId)) {
            return rejected("Report section id is not allowed for this session.", scope, null);
        }
        if (!StringUtils.hasText(markdown)) {
            return rejected("Report section markdown must not be blank.", scope, null);
        }

        try {
            var report = reportStore.upsertSection(
                    scope.reportId(),
                    new AnalysisReportSection(
                            sectionId,
                            normalize(title),
                            order,
                            markdown.trim(),
                            meta != null ? meta : AnalysisReportMeta.empty()
                    )
            );
            return ok("Report section saved.", scope, report, List.of(sectionId));
        } catch (CopilotReportSessionException exception) {
            return reportStore.current(scope.reportId()).isEmpty()
                    ? missingReport(exception.getMessage(), scope)
                    : rejected(exception.getMessage(), scope, null);
        }
    }

    @Tool(
            name = CopilotReportToolNames.PATCH_SECTION,
            description = """
                    Replaces exactly one matching fragment in an existing report section. Read the section with
                    report_get_current first, then pass its markdown SHA-256 digest and an exact oldText fragment.
                    The patch is atomic and rejects a stale digest, missing or repeated oldText, or an empty result.
                    The active report and allowed section ids come from hidden ToolContext; never provide reportId.
                    """
    )
    public CopilotReportToolResult patchSection(
            @ToolParam(description = "Canonical id of the existing allowed section.") String sectionId,
            @ToolParam(description = "Current section markdown SHA-256 from report_get_current manifest.")
            String expectedMarkdownSha256,
            @ToolParam(description = "Exact unique fragment to replace in the section markdown.") String oldText,
            @ToolParam(description = "Replacement fragment; may be empty to delete oldText without emptying the section.")
            String newText,
            @ToolParam(required = false, description = "Short Polish reason for this targeted correction.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }
        var normalizedSectionId = normalize(sectionId);
        if (!scope.allowedSectionIds().contains(normalizedSectionId)) {
            return rejected("Report section id is not allowed for this session.", scope, null);
        }
        try {
            var report = reportStore.patchSection(scope.reportId(), normalizedSectionId,
                    expectedMarkdownSha256, oldText, newText);
            return ok("Report section fragment patched.", scope, report, List.of(normalizedSectionId));
        } catch (CopilotReportSessionException exception) {
            return reportStore.current(scope.reportId()).isEmpty()
                    ? missingReport(exception.getMessage(), scope)
                    : rejected(exception.getMessage(), scope, null);
        }
    }

    @Tool(
            name = CopilotReportToolNames.UPDATE_HEADER,
            description = """
                    Updates report-level title fields in the current structured analysis report.
                    Use header for the primary result headline or detected problem. The active reportId is taken
                    from hidden ToolContext; never provide reportId as an argument.
                    """
    )
    public CopilotReportToolResult updateHeader(
            @ToolParam(description = "Primary report header, for example detected problem or report title.")
            String header,
            @ToolParam(required = false, description = "Optional report sub header.")
            String subHeader,
            @ToolParam(required = false, description = "Optional report markdown summary.")
            String markdownSummary,
            @ToolParam(required = false, description = "Short Polish reason why report header is being updated.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }
        if (!StringUtils.hasText(header)) {
            return rejected("Report header must not be blank.", scope, null);
        }

        try {
            var report = reportStore.updateHeader(
                    scope.reportId(),
                    header,
                    subHeader,
                    markdownSummary
            );
            return ok("Report header updated.", scope, report, List.of());
        } catch (CopilotReportSessionException exception) {
            return reportStore.current(scope.reportId()).isEmpty()
                    ? missingReport(exception.getMessage(), scope)
                    : rejected(exception.getMessage(), scope, null);
        }
    }

    @Tool(
            name = CopilotReportToolNames.UPDATE_META,
            description = """
                    Replaces report-level metadata in the current structured analysis report.
                    Use this for global references, visibilityLimits, openQuestions, gaps, confidence or warnings.
                    The active reportId is taken from hidden ToolContext; never provide reportId as an argument.
                    """
    )
    public CopilotReportToolResult updateMeta(
            @ToolParam(description = "Report-level metadata: references, visibilityLimits, openQuestions, gaps, confidence and warnings.")
            AnalysisReportMeta meta,
            @ToolParam(required = false, description = "Short Polish reason why report-level metadata is being updated.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }
        if (meta == null) {
            return rejected("Report meta must not be null.", scope, null);
        }

        try {
            var report = reportStore.updateMeta(scope.reportId(), meta);
            return ok("Report metadata updated.", scope, report, List.of());
        } catch (CopilotReportSessionException exception) {
            return reportStore.current(scope.reportId()).isEmpty()
                    ? missingReport(exception.getMessage(), scope)
                    : rejected(exception.getMessage(), scope, null);
        }
    }

    private CopilotReportToolResult ok(
            String message,
            ReportToolScope scope,
            AnalysisReport report,
            List<String> updatedSectionIds
    ) {
        return new CopilotReportToolResult(
                STATUS_OK,
                message,
                scope.reportId(),
                scope.reportFeature(),
                report != null ? CopilotReportManifestFactory.create(report, scope.allowedSectionIds()) : null,
                updatedSectionIds,
                scope.allowedSectionIds()
        );
    }

    private CopilotReportToolResult rejected(String message, ReportToolScope scope, AnalysisReport report) {
        return new CopilotReportToolResult(
                STATUS_REJECTED,
                message,
                scope.reportId(),
                scope.reportFeature(),
                report != null ? CopilotReportManifestFactory.create(report, scope.allowedSectionIds()) : null,
                List.of(),
                scope.allowedSectionIds()
        );
    }

    private CopilotReportToolResult missingReport(String message, ReportToolScope scope) {
        return new CopilotReportToolResult(
                STATUS_MISSING_REPORT,
                message,
                scope.reportId(),
                scope.reportFeature(),
                null,
                List.of(),
                scope.allowedSectionIds()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ReportToolScope(
            String reportId,
            String reportFeature,
            List<String> allowedSectionIds
    ) {

        static ReportToolScope from(ToolContext toolContext) {
            var context = toolContext != null && toolContext.getContext() != null
                    ? toolContext.getContext()
                    : Map.<String, Object>of();
            return new ReportToolScope(
                    stringValue(context.get(AgentToolContextKeys.REPORT_ID)),
                    stringValue(context.get(AgentToolContextKeys.REPORT_FEATURE)),
                    stringList(context.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS))
            );
        }

        private static String stringValue(Object value) {
            return value instanceof String stringValue && StringUtils.hasText(stringValue)
                    ? stringValue.trim()
                    : null;
        }

        private static List<String> stringList(Object value) {
            var values = new LinkedHashSet<String>();
            if (value instanceof Iterable<?> iterable) {
                iterable.forEach(item -> addText(values, item));
            } else if (value instanceof String stringValue) {
                Arrays.stream(stringValue.split(","))
                        .forEach(item -> addText(values, item));
            }
            return List.copyOf(values);
        }

        private static void addText(LinkedHashSet<String> values, Object value) {
            if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
                values.add(stringValue.trim());
            }
        }
    }
}
