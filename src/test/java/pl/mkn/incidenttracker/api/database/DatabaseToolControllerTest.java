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
                        "sandbox-a",
                        "oracle-dev",
                        "Dev database",
                        List.of(),
                        List.of("CRM_APP"),
                        List.of("Typed tools only."),
                        List.of()
                ));

        mockMvc.perform(post("/api/database/scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": {
                                    "correlationId": "corr-123",
                                    "environment": "sandbox-a"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("sandbox-a"))
                .andExpect(jsonPath("$.databaseAlias").value("oracle-dev"))
                .andExpect(jsonPath("$.allowedSchemas[0]").value("CRM_APP"));

        verify(databaseToolService).getScope(scope);
    }

    @Test
    void shouldDelegateFindTablesWithTypedRequest() throws Exception {
        var request = new DbFindTablesRequest("crm-service", "CUSTOMER_PROFILE", "CustomerProfileEntity", 10);
        when(databaseToolService.findTables(scope(FIND_TABLES), request))
                .thenReturn(new DbTableSearchResult(
                        "sandbox-a",
                        "oracle-dev",
                        "crm-service",
                        List.of("CRM_APP"),
                        List.of(new DbTableCandidate(
                                "CRM_APP",
                                "CUSTOMER_INTERACTION",
                                "TABLE",
                                "Customer interactions",
                                8,
                                List.of("ID"),
                                List.of("CUSTOMER_ID"),
                                1,
                                0,
                                List.of("matched CUSTOMER")
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
                                    "environment": "sandbox-a"
                                  },
                                  "request": {
                                    "applicationPattern": "crm-service",
                                    "tableNamePattern": "CUSTOMER_PROFILE",
                                    "entityOrKeywordHint": "CustomerProfileEntity",
                                    "limit": 10
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedApplication").value("crm-service"))
                .andExpect(jsonPath("$.candidates[0].schema").value("CRM_APP"))
                .andExpect(jsonPath("$.candidates[0].tableName").value("CUSTOMER_INTERACTION"));

        verify(databaseToolService).findTables(scope(FIND_TABLES), request);
    }

    @Test
    void shouldMapToolBadRequestToApiErrorResponse() throws Exception {
        var request = new DbCountRowsRequest(
                new DbTableRef("CRM_APP", "CUSTOMER_INTERACTION"),
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
                                    "environment": "sandbox-a"
                                  },
                                  "request": {
                                    "table": {
                                      "schema": "CRM_APP",
                                      "tableName": "CUSTOMER_INTERACTION"
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
                "sandbox-a",
                "database-console",
                "database-console",
                null,
                toolName
        );
    }
}
