package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbSampleRowsResult(
        String environment,
        String databaseAlias,
        DbTableRef table,
        List<String> columns,
        List<Map<String, Object>> rows,
        boolean truncated,
        List<String> warnings
) {
}
