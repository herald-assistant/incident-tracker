package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbTableSearchResult(
        String environment,
        String databaseAlias,
        String resolvedApplication,
        List<String> resolvedSchemas,
        List<DbTableCandidate> candidates,
        boolean truncated,
        List<String> warnings
) {
}
