package pl.mkn.tdw.aiplatform.copilot.tools.report;

import java.util.List;

public final class CopilotReportToolNames {

    public static final String PREFIX = "report_";
    public static final String GET_CURRENT = "report_get_current";
    public static final String UPSERT_SECTION = "report_upsert_section";
    public static final String UPDATE_HEADER = "report_update_header";
    public static final String UPDATE_META = "report_update_meta";

    private CopilotReportToolNames() {
    }

    public static List<String> allToolNames() {
        return List.of(GET_CURRENT, UPSERT_SECTION, UPDATE_HEADER, UPDATE_META);
    }

    public static boolean isReportTool(String toolName) {
        return allToolNames().contains(toolName);
    }
}
