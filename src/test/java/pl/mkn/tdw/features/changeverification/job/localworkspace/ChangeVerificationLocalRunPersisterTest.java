package pl.mkn.tdw.features.changeverification.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationComplianceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
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

class ChangeVerificationLocalRunPersisterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-20T10:05:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-20T10:06:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldPersistCompletedSnapshotAsLocalChangeVerificationRun() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new ChangeVerificationLocalRunPersister(objectMapper, store);

        persister.persistRunSnapshot(completedSnapshot());

        assertNotNull(store.savedEntry);
        assertNotNull(store.savedRecord);
        assertEquals("change-job-1", store.savedEntry.analysisId());
        assertEquals("change-verification", store.savedEntry.feature());
        assertEquals("CRM-123", store.savedEntry.name());
        assertEquals("COMPLETED", store.savedEntry.status());
        assertEquals(CREATED_AT, store.savedEntry.createdAt());
        assertEquals(UPDATED_AT, store.savedEntry.updatedAt());
        assertEquals(COMPLETED_AT, store.savedEntry.completedAt());

        assertEquals(LocalAnalysisRunRecord.SCHEMA, store.savedRecord.schema());
        assertEquals(LocalAnalysisRunRecord.VERSION, store.savedRecord.version());
        assertFalse(store.savedRecord.continuation().enabled());
        assertNull(store.savedRecord.continuation().copilotSessionId());

        var exportEnvelope = store.savedRecord.exportEnvelope();
        assertEquals("tdw.change-verification-export", exportEnvelope.path("schema").asText());
        assertEquals(4, exportEnvelope.path("version").asInt());
        assertEquals(COMPLETED_AT.toString(), exportEnvelope.path("exportedAt").asText());
        assertEquals("change-verification-analysis", exportEnvelope.at("/payload/type").asText());
        assertEquals("change-verification-result-v4", exportEnvelope.at("/payload/resultContract").asText());
        assertEquals("change-verification-result-v4", exportEnvelope.at("/payload/diagnostics/resultContract").asText());
        assertEquals("change-job-1", exportEnvelope.at("/payload/job/jobId").asText());
        assertEquals("CRM-123", exportEnvelope.at("/payload/job/issueKey").asText());
        assertEquals("change-report-1", exportEnvelope.at("/payload/job/report/reportId").asText());
    }

    @Test
    void shouldPersistRunningSnapshotWithoutReport() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = new ChangeVerificationLocalRunPersister(objectMapper, store);

        persister.persistRunSnapshot(runningSnapshot());

        assertNotNull(store.savedEntry);
        assertEquals("RUNNING", store.savedEntry.status());
        assertNull(store.savedEntry.completedAt());
        assertEquals(UPDATED_AT.toString(), store.savedRecord.exportEnvelope().path("exportedAt").asText());
        assertEquals("RUNNING", store.savedRecord.exportEnvelope().at("/payload/job/status").asText());
        assertEquals("change-verification-result-v4",
                store.savedRecord.exportEnvelope().at("/payload/diagnostics/resultContract").asText());
        assertFalse(store.savedRecord.continuation().enabled());
    }

    private ChangeVerificationJobStateSnapshot completedSnapshot() {
        var result = new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "Prepared prompt",
                new ChangeVerificationComplianceResponse(
                        true,
                        true,
                        "PASS_WITH_NOTES",
                        List.of(),
                        List.of(),
                        List.of("Keep acceptance criteria updated."),
                        List.of()
                ),
                null
        );
        return new ChangeVerificationJobStateSnapshot(
                "change-job-1",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                true,
                true,
                "gpt-5.4",
                "medium",
                "COMPLETED",
                null,
                null,
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Prepared prompt",
                result,
                report()
        );
    }

    private ChangeVerificationJobStateSnapshot runningSnapshot() {
        return new ChangeVerificationJobStateSnapshot(
                "change-job-1",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                true,
                true,
                "gpt-5.4",
                "medium",
                "RUNNING",
                "AI_VERIFICATION",
                "AI verification",
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                null,
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
                "change-report-1",
                "Change Verification: CRM-123",
                "CRM-123",
                "Summary",
                List.of(new AnalysisReportSection(
                        "COMPLIANCE",
                        "Compliance",
                        0,
                        "No blockers.",
                        AnalysisReportMeta.empty()
                )),
                AnalysisReportMeta.empty()
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
