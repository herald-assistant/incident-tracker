package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbMappingComparisonRequest(
        DbTableRef actualTable,
        List<ExpectedColumn> expectedColumns,
        List<ExpectedRelationship> expectedRelationships
) {
}
