package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbReadonlySqlResult(
        String environment,
        String databaseAlias,
        String reason,
        List<Map<String, Object>> rows,
        boolean truncated,
        List<String> warnings
) {
}
