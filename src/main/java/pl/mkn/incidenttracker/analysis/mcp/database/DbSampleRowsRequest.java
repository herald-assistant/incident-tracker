package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbSampleRowsRequest(
        DbTableRef table,
        List<String> columns,
        List<DbFilter> filters,
        List<DbOrderBy> orderBy,
        Integer limit
) {
}
