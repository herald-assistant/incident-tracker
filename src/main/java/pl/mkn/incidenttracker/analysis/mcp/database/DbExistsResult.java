package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbExistsResult(
        String environment,
        String databaseAlias,
        DbTableRef table,
        boolean exists,
        long count,
        List<Map<String, Object>> projection,
        List<String> warnings
) {
}
