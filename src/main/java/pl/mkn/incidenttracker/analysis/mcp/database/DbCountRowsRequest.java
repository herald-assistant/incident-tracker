package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbCountRowsRequest(
        DbTableRef table,
        List<DbFilter> filters
) {
}
