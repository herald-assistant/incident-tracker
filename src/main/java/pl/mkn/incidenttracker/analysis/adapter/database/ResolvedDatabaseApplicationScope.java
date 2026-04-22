package pl.mkn.incidenttracker.analysis.adapter.database;

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
