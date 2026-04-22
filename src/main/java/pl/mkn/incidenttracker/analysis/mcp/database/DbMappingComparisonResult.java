package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbMappingComparisonResult(
        String environment,
        String databaseAlias,
        DbTableRef actualTable,
        List<String> confirmedMatches,
        List<String> mismatches,
        List<String> warnings
) {
}
