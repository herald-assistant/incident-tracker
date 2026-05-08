package pl.mkn.incidenttracker.api.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.incidenttracker.integrations.database.DatabaseToolService;
import pl.mkn.incidenttracker.integrations.database.DbOperator;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames.COUNT_ROWS;
import static pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames.FIND_TABLES;
import static pl.mkn.incidenttracker.agenttools.database.DatabaseToolNames.GET_SCOPE;
import static pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatabaseToolController.class)
class DatabaseToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseToolService databaseToolService;

    @Test
    void shouldReturnDatabaseScopeForManualEnvironment() throws Exception {
        var scope = scope(GET_SCOPE);
        when(databaseToolService.getScope(scope))
                .thenReturn(new DbScopeResult(
                        "zt01",
                        "oracle-dev",
                        "Dev database",
                        List.of(),
                        List.of("ORDERS_APP"),
                        List.of("Typed tools only."),
                        List.of()
                ));

        mockMvc.perform(post("/api/database/scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": {
                                    "correlationId": "corr-123",
                                    "environment": "zt01"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("zt01"))
                .andExpect(jsonPath("$.databaseAlias").value("oracle-dev"))
                .andExpect(jsonPath("$.allowedSchemas[0]").value("ORDERS_APP"));

        verify(databaseToolService).getScope(scope);
    }

    @Test
    void shouldDelegateFindTablesWithTypedRequest() throws Exception {
        var request = new DbFindTablesRequest("orders-service", "ORDER", "OrderEntity", 10);
        when(databaseToolService.findTables(scope(FIND_TABLES), request))
                .thenReturn(new DbTableSearchResult(
                        "zt01",
                        "oracle-dev",
                        "orders-service",
                        List.of("ORDERS_APP"),
                        List.of(new DbTableCandidate(
                                "ORDERS_APP",
                                "ORDER_EVENT",
                                "TABLE",
                                "Order events",
                                8,
                                List.of("ID"),
                                List.of("ORDER_ID"),
                                1,
                                0,
                                List.of("matched ORDER")
                        )),
                        false,
                        List.of()
                ));

        mockMvc.perform(post("/api/database/tables/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": {
                                    "correlationId": "corr-123",
                                    "environment": "zt01"
                                  },
                                  "request": {
                                    "applicationPattern": "orders-service",
                                    "tableNamePattern": "ORDER",
                                    "entityOrKeywordHint": "OrderEntity",
                                    "limit": 10
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedApplication").value("orders-service"))
                .andExpect(jsonPath("$.candidates[0].schema").value("ORDERS_APP"))
                .andExpect(jsonPath("$.candidates[0].tableName").value("ORDER_EVENT"));

        verify(databaseToolService).findTables(scope(FIND_TABLES), request);
    }

    @Test
    void shouldMapToolBadRequestToApiErrorResponse() throws Exception {
        var request = new DbCountRowsRequest(
                new DbTableRef("ORDERS_APP", "ORDER_EVENT"),
                List.of(new DbFilter("STATUS", DbOperator.EQ, List.of("ACTIVE")))
        );
        when(databaseToolService.countRows(scope(COUNT_ROWS), request))
                .thenThrow(new IllegalArgumentException("Oracle table is outside allowed scope."));

        mockMvc.perform(post("/api/database/rows/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": {
                                    "correlationId": "corr-123",
                                    "environment": "zt01"
                                  },
                                  "request": {
                                    "table": {
                                      "schema": "ORDERS_APP",
                                      "tableName": "ORDER_EVENT"
                                    },
                                    "filters": [
                                      {
                                        "column": "STATUS",
                                        "operator": "EQ",
                                        "values": ["ACTIVE"]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATABASE_TOOL_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Oracle table is outside allowed scope."));
    }

    @Test
    void shouldValidateScopeEnvironment() throws Exception {
        mockMvc.perform(post("/api/database/scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": {
                                    "correlationId": "corr-123",
                                    "environment": " "
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field").isArray());

        verifyNoInteractions(databaseToolService);
    }

    private DbCapabilityScope scope(String toolName) {
        return new DbCapabilityScope(
                "corr-123",
                "zt01",
                "database-console",
                "database-console",
                null,
                toolName
        );
    }
}
