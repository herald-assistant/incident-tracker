package pl.mkn.incidenttracker.integrations.database;

import java.util.List;

public record ResolvedDatabaseApplicationScope(
        String environment,
        String databaseAlias,
        String applicationAlias,
        List<String> resolvedSchemas,
        List<String> relatedSchemas,
        List<String> matchedBecause,
        List<String> warnings
) {
}
