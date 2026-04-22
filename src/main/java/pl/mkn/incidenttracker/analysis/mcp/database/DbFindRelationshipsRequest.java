package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbFindRelationshipsRequest(
        List<DbTableRef> tables,
        Integer depth,
        Boolean includeInferred
) {
}
