package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.DbToolScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseReadOnlyQueryClient {

    private final DatabaseConnectionRouter connectionRouter;

    public long queryForCount(DbToolScope scope, String sqlTemplate, Map<String, ?> parameters) {
        var handle = connectionRouter.route(scope);
        var result = handle.namedParameterJdbcTemplate().queryForObject(
                sqlTemplate,
                new MapSqlParameterSource(parameters),
                Long.class
        );
        return result != null ? result : 0L;
    }

    public List<Map<String, Object>> queryForRows(DbToolScope scope, String sqlTemplate, Map<String, ?> parameters) {
        var handle = connectionRouter.route(scope);
        return handle.namedParameterJdbcTemplate().queryForList(
                sqlTemplate,
                new MapSqlParameterSource(parameters)
        );
    }

    public QueryCapture query(String sqlTemplate, Map<String, ?> parameters) {
        return new QueryCapture(sqlTemplate, new LinkedHashMap<>(parameters));
    }

    public record QueryCapture(
            String sqlTemplate,
            Map<String, ?> parameters
    ) {
    }
}
