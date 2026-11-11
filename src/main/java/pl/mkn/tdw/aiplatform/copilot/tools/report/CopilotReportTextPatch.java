package pl.mkn.tdw.aiplatform.copilot.tools.report;

import org.springframework.util.StringUtils;

/** Pure patch operation shared by publication preview and atomic report writes. */
public final class CopilotReportTextPatch {
    private CopilotReportTextPatch() {}

    public static String apply(String markdown, String oldText, String newText) {
        if (!StringUtils.hasText(oldText) || newText == null) {
            throw new CopilotReportSessionException("oldText and newText are required.");
        }
        markdown = markdown != null ? markdown : "";
        var start = markdown.indexOf(oldText);
        if (start < 0) {
            throw new CopilotReportSessionException("oldText was not found in the report section.");
        }
        if (markdown.indexOf(oldText, start + 1) >= 0) {
            throw new CopilotReportSessionException("oldText occurs more than once in the report section.");
        }
        var updated = markdown.substring(0, start) + newText + markdown.substring(start + oldText.length());
        if (!StringUtils.hasText(updated) || markdown.equals(updated)) {
            throw new CopilotReportSessionException("Patch must leave a non-empty changed section.");
        }
        return updated;
    }
}
