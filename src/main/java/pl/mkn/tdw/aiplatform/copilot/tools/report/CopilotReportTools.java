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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CopilotReportTools {

    private static final String STATUS_OK = "ok";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_MISSING_REPORT = "missing_report";

    private final CopilotReportSessionStore reportStore;

    @Tool(
            name = CopilotReportToolNames.GET_CURRENT,
            description = """
                    Returns the current structured analysis report for this AI session.
                    The active report is selected from hidden ToolContext. Do not provide reportId, analysisId,
                    correlationId, environment, gitLabGroup or gitLabBranch.
                    """
    )
    public CopilotReportToolResult getCurrentReport(
            @ToolParam(required = false, description = "Short Polish reason why the current report is needed.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = ReportToolScope.from(toolContext);
        if (!StringUtils.hasText(scope.reportId())) {
            return missingReport("No active reportId is available in hidden ToolContext.", scope);
        }

        return reportStore.current(scope.reportId())
                .map(report -> ok("Current report returned.", scope, report, List.of()))
                .orElseGet(() -> missingReport("No active report is registered for this reportId.", scope));
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
                report,
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
                report,
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
