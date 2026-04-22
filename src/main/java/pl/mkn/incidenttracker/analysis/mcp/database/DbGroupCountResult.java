package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbGroupCountResult(
        String environment,
        String databaseAlias,
        DbTableRef table,
        List<Map<String, Object>> groups,
        boolean truncated,
        List<String> warnings
) {
}
