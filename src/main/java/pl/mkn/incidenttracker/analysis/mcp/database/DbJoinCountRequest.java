package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbJoinCountRequest(
        List<DbTableRef> tables,
        List<DbJoinCondition> joins,
        List<DbQualifiedFilter> filters
) {
}
