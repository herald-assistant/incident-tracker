package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbQualifiedFilter(
        DbColumnRef column,
        DbOperator operator,
        List<String> values
) {
}
