package pl.mkn.incidenttracker.analysis.mcp.database;

import java.util.List;

public record DbExistsByKeyRequest(
        DbTableRef table,
        List<DbKeyValue> keyValues,
        List<String> projectionColumns
) {
}
