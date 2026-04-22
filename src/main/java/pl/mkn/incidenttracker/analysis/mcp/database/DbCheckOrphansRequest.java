package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbCheckOrphansRequest(
        DbTableRef childTable,
        String childColumn,
        DbTableRef parentTable,
        String parentColumn,
        List<DbFilter> childFilters,
        Integer sampleLimit
) {
}
