package pl.mkn.tdw.features.incidentanalysis.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;
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

class IncidentAnalysisLocalRunPersisterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T10:05:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:06:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldPersistCompletedSnapshotAsLocalRun() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new IncidentAnalysisLocalRunPersister(objectMapper, store);

        persister.persistCompletedInitialRun(completedSnapshot("COMPLETED"), aiRequest(), "copilot-session-1");

        assertNotNull(store.savedEntry);
        assertNotNull(store.savedRecord);
        assertEquals("analysis-1", store.savedEntry.analysisId());
        assertEquals("incident-analysis", store.savedEntry.feature());
        assertEquals("corr-123", store.savedEntry.name());
        assertEquals("COMPLETED", store.savedEntry.status());
        assertEquals(CREATED_AT, store.savedEntry.createdAt());
        assertEquals(UPDATED_AT, store.savedEntry.updatedAt());
        assertEquals(COMPLETED_AT, store.savedEntry.completedAt());

        assertEquals(LocalAnalysisRunRecord.SCHEMA, store.savedRecord.schema());
        assertEquals(LocalAnalysisRunRecord.VERSION, store.savedRecord.version());
        assertTrue(store.savedRecord.continuation().enabled());
        assertEquals("CRM/runtime", store.savedRecord.continuation().gitLabGroup());
        assertEquals("GITHUB_APP", store.savedRecord.continuation().authMode());
        assertEquals("operator-session-1", store.savedRecord.continuation().authPrincipalRef());
        assertEquals("copilot-session-1", store.savedRecord.continuation().copilotSessionId());
        assertEquals("github-copilot-sdk", store.savedRecord.continuation().copilotRuntime());
        assertEquals("copilot-session", store.savedRecord.continuation().continuationMode());

        var exportEnvelope = store.savedRecord.exportEnvelope();
        assertEquals("tdw.analysis-export", exportEnvelope.path("schema").asText());
        assertEquals(6, exportEnvelope.path("version").asInt());
        assertEquals(COMPLETED_AT.toString(), exportEnvelope.path("exportedAt").asText());
        assertEquals("analysis-job", exportEnvelope.at("/payload/type").asText());
        assertEquals("analysis-1", exportEnvelope.at("/payload/job/analysisId").asText());
        assertEquals("corr-123", exportEnvelope.at("/payload/job/correlationId").asText());
        assertEquals("incident-report-1", exportEnvelope.at("/payload/job/report/reportId").asText());
        assertEquals("DOWNSTREAM_TIMEOUT", exportEnvelope.at("/payload/job/report/header").asText());
        assertEquals("Prepared prompt", exportEnvelope.at("/payload/job/preparedPrompt").asText());
        assertFalse(exportEnvelope.toString().contains("operator-session-1"));
        assertFalse(exportEnvelope.toString().contains("copilot-session-1"));
    }

    @Test
    void shouldPersistNonCompletedSnapshotWithContinuationDisabled() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new IncidentAnalysisLocalRunPersister(objectMapper, store);

        persister.persistRunSnapshot(failedSnapshot());

        assertNotNull(store.savedEntry);
        assertNotNull(store.savedRecord);
        assertEquals("analysis-1", store.savedEntry.analysisId());
        assertEquals("FAILED", store.savedEntry.status());
        assertEquals(COMPLETED_AT, store.savedEntry.completedAt());
        assertFalse(store.savedRecord.continuation().enabled());
        assertEquals("FAILED", store.savedRecord.exportEnvelope().at("/payload/job/status").asText());
        assertEquals("AI gateway timeout", store.savedRecord.exportEnvelope().at("/payload/job/errorMessage").asText());
        assertEquals("corr-123", store.savedRecord.exportEnvelope().at("/payload/job/correlationId").asText());
    }

    private AnalysisJobStateSnapshot completedSnapshot(String status) {
        return new AnalysisJobStateSnapshot(
                "analysis-1",
                "corr-123",
                "gpt-5.4",
                "medium",
                status,
                null,
                null,
                "dev3",
                "main",
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Prepared prompt",
                new AnalysisResultResponse(
                        status,
                        "corr-123",
                        "dev3",
                        "main",
                        "DOWNSTREAM_TIMEOUT",
                        "Catalog process",
                        "Catalog Context",
                        "Core Team",
                        "Functional analysis",
                        "Technical analysis",
                        "medium",
                        List.of("Limited downstream visibility."),
                        "Final prompt"
                ),
                report()
        );
    }

    private AnalysisJobStateSnapshot failedSnapshot() {
        return new AnalysisJobStateSnapshot(
                "analysis-1",
                "corr-123",
                "gpt-5.4",
                "medium",
                "FAILED",
                null,
                null,
                "dev3",
                "main",
                "ANALYSIS_FAILED",
                "AI gateway timeout",
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Prepared prompt",
                null,
                null
        );
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "incident-report-1",
                "DOWNSTREAM_TIMEOUT",
                "dev3 | main",
                "",
                List.of(
                        new AnalysisReportSection(
                                "FUNCTIONAL_ANALYSIS",
                                "Functional analysis",
                                1,
                                "Functional analysis",
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                "TECHNICAL_HANDOFF",
                                "Technical handoff",
                                2,
                                "Technical analysis",
                                AnalysisReportMeta.empty()
                        )
                ),
                AnalysisReportMeta.empty()
        );
    }

    private InitialAnalysisRequest aiRequest() {
        return new InitialAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "CRM/runtime",
                List.of(),
                AnalysisAiOptions.DEFAULT,
                AnalysisAiAuthRef.githubApp("operator-session-1", "octocat")
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
