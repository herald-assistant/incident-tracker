package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;
import java.util.Map;

public record DbJoinSampleResult(
        String environment,
        String databaseAlias,
        List<Map<String, Object>> rows,
        boolean truncated,
        List<String> warnings
) {
}
