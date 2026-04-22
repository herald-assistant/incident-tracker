package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbCountResult(
        String environment,
        String databaseAlias,
        DbTableRef table,
        long count,
        List<String> appliedFilters,
        List<String> warnings
) {
}
