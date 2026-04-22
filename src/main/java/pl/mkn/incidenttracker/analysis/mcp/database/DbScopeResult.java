package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbScopeResult(
        String environment,
        String databaseAlias,
        String description,
        List<DbApplicationScopeInfo> applications,
        List<String> allowedSchemas,
        List<String> safetyRules,
        List<String> warnings
) {
}
