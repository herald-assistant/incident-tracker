package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbJoinSampleRequest(
        List<DbTableRef> tables,
        List<DbJoinCondition> joins,
        List<DbProjectedColumn> columns,
        List<DbQualifiedFilter> filters,
        Integer limit
) {
}
