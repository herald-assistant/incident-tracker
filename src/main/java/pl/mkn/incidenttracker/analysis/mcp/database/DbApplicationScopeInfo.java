package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbApplicationScopeInfo(
        String applicationAlias,
        String schema,
        List<String> relatedSchemas,
        String description,
        List<String> patterns
) {
}
