package pl.mkn.incidenttracker.api.database;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.integrations.database.DatabaseToolService;

import java.util.function.Supplier;

import static pl.mkn.incidenttracker.api.database.DatabaseToolApiDtos.*;
import static pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.*;

@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
public class DatabaseToolController {

    private static final String GET_SCOPE_OPERATION = "database.scope";
    private static final String FIND_TABLES_OPERATION = "database.tables.search";
    private static final String FIND_COLUMNS_OPERATION = "database.columns.search";
    private static final String DESCRIBE_TABLE_OPERATION = "database.tables.describe";
    private static final String EXISTS_BY_KEY_OPERATION = "database.rows.exists-by-key";
    private static final String COUNT_ROWS_OPERATION = "database.rows.count";
    private static final String GROUP_COUNT_OPERATION = "database.rows.group-count";
    private static final String SAMPLE_ROWS_OPERATION = "database.rows.sample";
    private static final String CHECK_ORPHANS_OPERATION = "database.relationships.orphans";
    private static final String FIND_RELATIONSHIPS_OPERATION = "database.relationships.search";
    private static final String JOIN_COUNT_OPERATION = "database.joins.count";
    private static final String JOIN_SAMPLE_OPERATION = "database.joins.sample";
    private static final String COMPARE_MAPPING_OPERATION = "database.mappings.compare-table";
    private static final String READONLY_SQL_OPERATION = "database.sql.readonly";

    private final ObjectProvider<DatabaseToolService> databaseToolService;

    @PostMapping("/scope")
    public DbScopeResult getScope(@Valid @RequestBody DatabaseGetScopeApiRequest request) {
        return invoke(() -> service().getScope(request.scope().toCapabilityScope(GET_SCOPE_OPERATION)));
    }

    @PostMapping("/tables/search")
    public DbTableSearchResult findTables(@Valid @RequestBody DatabaseFindTablesApiRequest request) {
        return invoke(() -> service().findTables(
                request.scope().toCapabilityScope(FIND_TABLES_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/columns/search")
    public DbColumnSearchResult findColumns(@Valid @RequestBody DatabaseFindColumnsApiRequest request) {
        return invoke(() -> service().findColumns(
                request.scope().toCapabilityScope(FIND_COLUMNS_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/tables/describe")
    public DbTableDescription describeTable(@Valid @RequestBody DatabaseDescribeTableApiRequest request) {
        return invoke(() -> service().describeTable(
                request.scope().toCapabilityScope(DESCRIBE_TABLE_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/rows/exists-by-key")
    public DbExistsResult existsByKey(@Valid @RequestBody DatabaseExistsByKeyApiRequest request) {
        return invoke(() -> service().existsByKey(
                request.scope().toCapabilityScope(EXISTS_BY_KEY_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/rows/count")
    public DbCountResult countRows(@Valid @RequestBody DatabaseCountRowsApiRequest request) {
        return invoke(() -> service().countRows(
                request.scope().toCapabilityScope(COUNT_ROWS_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/rows/group-count")
    public DbGroupCountResult groupCount(@Valid @RequestBody DatabaseGroupCountApiRequest request) {
        return invoke(() -> service().groupCount(
                request.scope().toCapabilityScope(GROUP_COUNT_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/rows/sample")
    public DbSampleRowsResult sampleRows(@Valid @RequestBody DatabaseSampleRowsApiRequest request) {
        return invoke(() -> service().sampleRows(
                request.scope().toCapabilityScope(SAMPLE_ROWS_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/relationships/orphans")
    public DbOrphanCheckResult checkOrphans(@Valid @RequestBody DatabaseCheckOrphansApiRequest request) {
        return invoke(() -> service().checkOrphans(
                request.scope().toCapabilityScope(CHECK_ORPHANS_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/relationships/search")
    public DbRelationshipsResult findRelationships(@Valid @RequestBody DatabaseFindRelationshipsApiRequest request) {
        return invoke(() -> service().findRelationships(
                request.scope().toCapabilityScope(FIND_RELATIONSHIPS_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/joins/count")
    public DbJoinCountResult joinCount(@Valid @RequestBody DatabaseJoinCountApiRequest request) {
        return invoke(() -> service().joinCount(
                request.scope().toCapabilityScope(JOIN_COUNT_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/joins/sample")
    public DbJoinSampleResult joinSample(@Valid @RequestBody DatabaseJoinSampleApiRequest request) {
        return invoke(() -> service().joinSample(
                request.scope().toCapabilityScope(JOIN_SAMPLE_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/mappings/compare-table")
    public DbMappingComparisonResult compareTableToExpectedMapping(
            @Valid @RequestBody DatabaseMappingComparisonApiRequest request
    ) {
        return invoke(() -> service().compareTableToExpectedMapping(
                request.scope().toCapabilityScope(COMPARE_MAPPING_OPERATION),
                request.request()
        ));
    }

    @PostMapping("/sql/readonly")
    public DbReadonlySqlResult executeReadonlySql(@Valid @RequestBody DatabaseReadonlySqlApiRequest request) {
        return invoke(() -> service().executeReadonlySql(
                request.scope().toCapabilityScope(READONLY_SQL_OPERATION),
                request.request()
        ));
    }

    private DatabaseToolService service() {
        var service = databaseToolService.getIfAvailable();
        if (service == null) {
            throw DatabaseToolApiException.disabled();
        }
        return service;
    }

    private <T> T invoke(Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (DatabaseToolApiException exception) {
            throw exception;
        }
        catch (IllegalArgumentException exception) {
            throw DatabaseToolApiException.badRequest(exception.getMessage());
        }
        catch (IllegalStateException exception) {
            throw DatabaseToolApiException.conflict(exception.getMessage());
        }
    }
}
