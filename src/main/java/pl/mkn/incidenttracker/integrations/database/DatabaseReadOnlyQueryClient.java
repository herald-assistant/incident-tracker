package pl.mkn.incidenttracker.integrations.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.DbCapabilityScope;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseReadOnlyQueryClient {

    private final DatabaseConnectionRouter connectionRouter;

    public long queryForCount(DbCapabilityScope scope, String sqlTemplate, Map<String, ?> parameters) {
        var handle = connectionRouter.route(scope);
        var result = handle.namedParameterJdbcTemplate().queryForObject(
                sqlTemplate,
                new MapSqlParameterSource(parameters),
                Long.class
        );
        return result != null ? result : 0L;
    }

    public List<Map<String, Object>> queryForRows(DbCapabilityScope scope, String sqlTemplate, Map<String, ?> parameters) {
        var handle = connectionRouter.route(scope);
        return handle.namedParameterJdbcTemplate().queryForList(
                sqlTemplate,
                new MapSqlParameterSource(parameters)
        );
    }
}
