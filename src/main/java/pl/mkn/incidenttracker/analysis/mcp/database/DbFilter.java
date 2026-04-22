package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbFilter(
        String column,
        DbOperator operator,
        List<String> values
) {
}
