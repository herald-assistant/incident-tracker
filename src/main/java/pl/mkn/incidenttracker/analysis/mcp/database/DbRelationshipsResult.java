package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbRelationshipsResult(
        String environment,
        String databaseAlias,
        List<DbRelationshipHint> relationships,
        List<String> warnings
) {
}
