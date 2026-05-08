package pl.mkn.incidenttracker.api.database;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.DbCapabilityScope;

import static pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.*;

public final class DatabaseToolApiDtos {

    private static final String MANUAL_RUN_REFERENCE = "database-console";

    private DatabaseToolApiDtos() {
    }

    public record DatabaseToolScopeRequest(
            String correlationId,
            @NotBlank(message = "environment must not be blank")
            String environment,
            String analysisRunId
    ) {

        DbCapabilityScope toCapabilityScope(String toolName) {
            return new DbCapabilityScope(
                    defaultText(correlationId, "manual-database-console"),
                    environment.trim(),
                    defaultText(analysisRunId, MANUAL_RUN_REFERENCE),
                    MANUAL_RUN_REFERENCE,
                    null,
                    toolName
            );
        }
    }

    public record DatabaseGetScopeApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope
    ) {
    }

    public record DatabaseFindTablesApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbFindTablesRequest request
    ) {
    }

    public record DatabaseFindColumnsApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbFindColumnsRequest request
    ) {
    }

    public record DatabaseDescribeTableApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbDescribeTableRequest request
    ) {
    }

    public record DatabaseExistsByKeyApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbExistsByKeyRequest request
    ) {
    }

    public record DatabaseCountRowsApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbCountRowsRequest request
    ) {
    }

    public record DatabaseGroupCountApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbGroupCountRequest request
    ) {
    }

    public record DatabaseSampleRowsApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbSampleRowsRequest request
    ) {
    }

    public record DatabaseCheckOrphansApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbCheckOrphansRequest request
    ) {
    }

    public record DatabaseFindRelationshipsApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbFindRelationshipsRequest request
    ) {
    }

    public record DatabaseJoinCountApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbJoinCountRequest request
    ) {
    }

    public record DatabaseJoinSampleApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbJoinSampleRequest request
    ) {
    }

    public record DatabaseMappingComparisonApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbMappingComparisonRequest request
    ) {
    }

    public record DatabaseReadonlySqlApiRequest(
            @Valid
            @NotNull(message = "scope must not be null")
            DatabaseToolScopeRequest scope,
            @NotNull(message = "request must not be null")
            DbReadonlySqlRequest request
    ) {
    }

    private static String defaultText(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }
}
