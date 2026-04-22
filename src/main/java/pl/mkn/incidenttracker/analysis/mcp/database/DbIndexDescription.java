package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbIndexDescription(
        String indexName,
        boolean unique,
        List<String> columns
) {
}
