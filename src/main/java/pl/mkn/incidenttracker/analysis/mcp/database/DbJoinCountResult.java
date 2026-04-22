package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbJoinCountResult(
        String environment,
        String databaseAlias,
        long count,
        List<String> warnings
) {
}
