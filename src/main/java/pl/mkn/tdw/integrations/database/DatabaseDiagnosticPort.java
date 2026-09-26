package pl.mkn.tdw.integrations.database;

import static pl.mkn.tdw.integrations.database.contract.DatabaseCapabilityDtos.*;

public interface DatabaseDiagnosticPort {

    DbScopeResult getScope(DbCapabilityScope scope);

    DbTableSearchResult findTables(DbCapabilityScope scope, DbFindTablesRequest request);

    DbColumnSearchResult findColumns(DbCapabilityScope scope, DbFindColumnsRequest request);

    DbTableDescription describeTable(DbCapabilityScope scope, DbDescribeTableRequest request);

    DbExistsResult existsByKey(DbCapabilityScope scope, DbExistsByKeyRequest request);

    DbCountResult countRows(DbCapabilityScope scope, DbCountRowsRequest request);

    DbGroupCountResult groupCount(DbCapabilityScope scope, DbGroupCountRequest request);

    DbSampleRowsResult sampleRows(DbCapabilityScope scope, DbSampleRowsRequest request);

    DbOrphanCheckResult checkOrphans(DbCapabilityScope scope, DbCheckOrphansRequest request);

    DbRelationshipsResult findRelationships(DbCapabilityScope scope, DbFindRelationshipsRequest request);

    DbJoinCountResult joinCount(DbCapabilityScope scope, DbJoinCountRequest request);

    DbJoinSampleResult joinSample(DbCapabilityScope scope, DbJoinSampleRequest request);

    DbMappingComparisonResult compareTableToExpectedMapping(
            DbCapabilityScope scope,
            DbMappingComparisonRequest request
    );

    DbReadonlySqlResult executeReadonlySql(DbCapabilityScope scope, DbReadonlySqlRequest request);
}
