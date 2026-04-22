package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbColumnSearchResult(
        String environment,
        String databaseAlias,
        String resolvedApplication,
        List<String> resolvedSchemas,
        List<DbColumnCandidate> candidates,
        boolean truncated,
        List<String> warnings
) {
}
