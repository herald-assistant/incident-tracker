package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbDescribeTablesRequest(
        List<DbTableRef> tables
) {
}
