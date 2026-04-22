package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbOrphanCheckResult(
        String environment,
        String databaseAlias,
        DbTableRef childTable,
        String childColumn,
        DbTableRef parentTable,
        String parentColumn,
        long orphanCount,
        List<Map<String, Object>> sampleRows,
        boolean truncated,
        List<String> warnings
) {
}
