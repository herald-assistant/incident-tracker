package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbGroupCountRequest(
        DbTableRef table,
        List<String> groupByColumns,
        List<DbFilter> filters,
        Integer limit
) {
}
