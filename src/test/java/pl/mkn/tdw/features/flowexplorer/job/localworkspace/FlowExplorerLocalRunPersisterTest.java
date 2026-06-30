package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerLocalRunPersisterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T10:05:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:06:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldPersistCompletedSnapshotAsLocalFlowExplorerRun() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new FlowExplorerLocalRunPersister(objectMapper, store);

        persister.persistCompletedInitialRun(
                completedSnapshot("COMPLETED"),
                AnalysisAiAuthRef.githubApp("operator-session-1", "octocat"),
                "copilot-session-1"
        );

        assertNotNull(store.savedEntry);
        assertNotNull(store.savedRecord);
        assertEquals("flow-job-1", store.savedEntry.analysisId());
        assertEquals("flow-explorer", store.savedEntry.feature());
        assertEquals("GET /api/customers/{id} / DEEP_DISCOVERY", store.savedEntry.name());
        assertEquals("COMPLETED", store.savedEntry.status());
        assertEquals(CREATED_AT, store.savedEntry.createdAt());
        assertEquals(UPDATED_AT, store.savedEntry.updatedAt());
        assertEquals(COMPLETED_AT, store.savedEntry.completedAt());

        assertEquals(LocalAnalysisRunRecord.SCHEMA, store.savedRecord.schema());
        assertEquals(LocalAnalysisRunRecord.VERSION, store.savedRecord.version());
        assertTrue(store.savedRecord.continuation().enabled());
        assertNull(store.savedRecord.continuation().gitLabGroup());
        assertEquals("GITHUB_APP", store.savedRecord.continuation().authMode());
        assertEquals("operator-session-1", store.savedRecord.continuation().authPrincipalRef());
        assertEquals("copilot-session-1", store.savedRecord.continuation().copilotSessionId());
        assertEquals("github-copilot-sdk", store.savedRecord.continuation().copilotRuntime());
        assertEquals("copilot-session", store.savedRecord.continuation().continuationMode());

        var exportEnvelope = store.savedRecord.exportEnvelope();
        assertEquals("tdw.flow-explorer-export", exportEnvelope.path("schema").asText());
        assertEquals(2, exportEnvelope.path("version").asInt());
        assertEquals(COMPLETED_AT.toString(), exportEnvelope.path("exportedAt").asText());
        assertEquals("flow-explorer-analysis", exportEnvelope.at("/payload/type").asText());
        assertEquals("flow-job-1", exportEnvelope.at("/payload/job/jobId").asText());
        assertEquals("GET", exportEnvelope.at("/payload/job/httpMethod").asText());
        assertEquals("/api/customers/{id}", exportEnvelope.at("/payload/job/endpointPath").asText());
        assertEquals("flow-report-1", exportEnvelope.at("/payload/job/report/reportId").asText());
        assertEquals("Flow Explorer: GET /api/customers/{id}", exportEnvelope.at("/payload/job/report/header").asText());
        assertEquals("flow-explorer-goal-result-v1", exportEnvelope.at("/payload/diagnostics/resultContract").asText());
        assertFalse(exportEnvelope.toString().contains("operator-session-1"));
        assertFalse(exportEnvelope.toString().contains("copilot-session-1"));
    }

    @Test
    void shouldSkipNonCompletedSnapshots() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new FlowExplorerLocalRunPersister(objectMapper, store);

        persister.persistCompletedInitialRun(
                completedSnapshot("FAILED"),
                AnalysisAiAuthRef.localToken(null),
                "copilot-session-1"
        );

        assertNull(store.savedEntry);
        assertNull(store.savedRecord);
    }

    @Test
    void shouldDisableContinuationWhenCopilotSessionIdIsMissing() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new FlowExplorerLocalRunPersister(objectMapper, store);

        persister.persistCompletedInitialRun(
                completedSnapshot("COMPLETED"),
                AnalysisAiAuthRef.localToken(null),
                null
        );

        assertNotNull(store.savedRecord);
        assertFalse(store.savedRecord.continuation().enabled());
        assertNull(store.savedRecord.continuation().copilotSessionId());
    }

    private FlowExplorerJobStateSnapshot completedSnapshot(String status) {
        return new FlowExplorerJobStateSnapshot(
                "flow-job-1",
                "crm-service",
                "crm-api:GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                "main",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                sectionModes(),
                "gpt-5.4",
                "medium",
                status,
                null,
                null,
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Prepared prompt",
                new FlowExplorerResultResponse(
                        status,
                        "crm-service",
                        "crm-api:GET:/api/customers/{id}",
                        "GET",
                        "/api/customers/{id}",
                        "main",
                        FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                        "Prepared prompt",
                        aiResponse(),
                        new AnalysisAiUsage(10, 5, 0, 0, 15, 0.01, 1000, 1, "gpt-5.4", null, null, null)
                ),
                report()
        );
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "flow-report-1",
                "Flow Explorer: GET /api/customers/{id}",
                "crm-service | main | DEEP_DISCOVERY",
                "",
                List.of(
                        new AnalysisReportSection(
                                "OVERVIEW",
                                "Overview",
                                0,
                                "Overview",
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                "FUNCTIONAL_FLOW",
                                "Functional flow",
                                1,
                                "Section Functional flow",
                                AnalysisReportMeta.empty()
                        )
                ),
                AnalysisReportMeta.empty()
        );
    }

    private List<FlowExplorerResultSectionModeAssignment> sectionModes() {
        return List.of(
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                        "Functional flow",
                        FlowExplorerResultSectionMode.DEEP
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.VALIDATIONS,
                        "Validations",
                        FlowExplorerResultSectionMode.COMPACT
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.PERSISTENCE,
                        "Persistence",
                        FlowExplorerResultSectionMode.COMPACT
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.INTEGRATIONS,
                        "Integrations",
                        FlowExplorerResultSectionMode.COMPACT
                )
        );
    }

    private FlowExplorerAiResponse aiResponse() {
        return new FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview("Overview", "high", List.of()),
                sectionModes().stream()
                        .map(sectionMode -> new FlowExplorerResultSection(
                                sectionMode.id(),
                                sectionMode.title(),
                                sectionMode.mode(),
                                "Section " + sectionMode.title(),
                                List.of(),
                                List.of(),
                                List.of()
                        ))
                        .toList(),
                List.of(),
                List.of(),
                List.of(),
                "high",
                List.of("Dopytaj o walidacje.")
        );
    }

    private static final class CapturingLocalAnalysisRunStore implements LocalAnalysisRunStore {

        private LocalAnalysisRunIndexEntry savedEntry;
        private LocalAnalysisRunRecord savedRecord;

        @Override
        public List<LocalAnalysisRunIndexEntry> listRuns() {
            return savedEntry != null ? List.of(savedEntry) : List.of();
        }

        @Override
        public Optional<LocalAnalysisRunRecord> findById(String analysisId) {
            return Optional.ofNullable(savedRecord);
        }

        @Override
        public void save(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
            savedEntry = indexEntry;
            savedRecord = record;
        }

        @Override
        public void rename(String analysisId, String name) {
        }

        @Override
        public void delete(String analysisId) {
        }
    }
}
