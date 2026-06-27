package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatResult;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisRunHistoryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T10:05:00Z");
    private static final Instant RENAMED_AT = Instant.parse("2026-06-21T08:30:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:06:00Z");
    private static final Instant CHAT_UPDATED_AT = Instant.parse("2026-06-20T10:10:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldListRunsFromIndexOnly() throws Exception {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), record("analysis-1", true));
        var service = new AnalysisRunHistoryService(store);

        var response = service.listRuns();

        assertEquals(1, response.runs().size());
        assertEquals("analysis-1", response.runs().get(0).analysisId());
        assertEquals("incident-analysis", response.runs().get(0).feature());
        assertEquals("corr-123", response.runs().get(0).name());
        assertEquals(CREATED_AT, response.runs().get(0).createdAt());
        assertEquals(UPDATED_AT, response.runs().get(0).updatedAt());
        assertEquals(COMPLETED_AT, response.runs().get(0).completedAt());
        assertEquals(0, store.findByIdCalls);

        var serialized = objectMapper.writeValueAsString(response);
        assertFalse(serialized.contains("runPath"));
        assertFalse(serialized.contains("Prepared prompt"));
        assertFalse(serialized.contains("CRM/runtime"));
        assertFalse(serialized.contains("LOCAL_TOKEN"));
        assertFalse(serialized.contains("local-token"));
    }

    @Test
    void shouldReturnSafeDetail() throws Exception {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), record("analysis-1", true));
        var service = new AnalysisRunHistoryService(store);

        var response = service.getRun(" analysis-1 ");

        assertEquals("analysis-1", response.analysisId());
        assertEquals("incident-analysis", response.feature());
        assertEquals("corr-123", response.name());
        assertEquals("tdw.analysis-export", response.exportEnvelope().path("schema").asText());
        assertEquals(6, response.exportEnvelope().path("version").asInt());
        assertEquals("analysis-1", response.exportEnvelope().at("/payload/job/analysisId").asText());
        assertTrue(response.continuationEnabled());
        assertEquals(1, store.findByIdCalls);

        var serialized = objectMapper.writeValueAsString(response);
        assertFalse(serialized.contains("runPath"));
        assertFalse(serialized.contains("CRM/runtime"));
        assertFalse(serialized.contains("LOCAL_TOKEN"));
        assertFalse(serialized.contains("local-token"));
    }

    @Test
    void shouldExportStoredEnvelopeWithoutContinuationMetadata() throws Exception {
        var store = new CapturingLocalAnalysisRunStore();
        var continuation = new LocalAnalysisRunContinuation(
                true,
                "CRM/runtime",
                "LOCAL_TOKEN",
                "local-token"
        ).withLatestCopilotSession("copilot-session-123");
        store.addRun(
                entry("analysis-1", "incident-analysis", "corr-123"),
                recordWithPrompt("analysis-1", "Prepared prompt", continuation)
        );
        var service = new AnalysisRunHistoryService(store);

        var exported = service.exportRun(" analysis-1 ");

        assertEquals("tdw.analysis-export", exported.path("schema").asText());
        assertEquals(6, exported.path("version").asInt());
        assertEquals("analysis-1", exported.at("/payload/job/analysisId").asText());
        assertEquals("Prepared prompt", exported.at("/payload/job/preparedPrompt").asText());
        assertEquals(1, store.findByIdCalls);

        var serialized = objectMapper.writeValueAsString(exported);
        assertTrue(serialized.contains("Prepared prompt"));
        assertFalse(serialized.contains("continuation"));
        assertFalse(serialized.contains("copilotSessionId"));
        assertFalse(serialized.contains("copilot-session-123"));
        assertFalse(serialized.contains("github-copilot-sdk"));
        assertFalse(serialized.contains("copilot-session"));
        assertFalse(serialized.contains("CRM/runtime"));
        assertFalse(serialized.contains("LOCAL_TOKEN"));
        assertFalse(serialized.contains("local-token"));
        assertFalse(serialized.contains("runPath"));
        assertFalse(serialized.contains("tokens.json"));
    }

    @Test
    void shouldRenameExistingRunAndReturnUpdatedDetail() {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), record("analysis-1", true));
        var service = new AnalysisRunHistoryService(store);

        var response = service.renameRun(" analysis-1 ", " Awaria koszyka w dev3 ");

        assertEquals("analysis-1", store.renamedAnalysisId);
        assertEquals("Awaria koszyka w dev3", store.renamedName);
        assertEquals("Awaria koszyka w dev3", response.name());
        assertEquals(RENAMED_AT, response.updatedAt());
    }

    @Test
    void shouldSendChatMessageThroughFeatureHandlerAndSaveUpdatedRun() {
        var store = new CapturingLocalAnalysisRunStore();
        var originalRecord = record("analysis-1", true);
        var updatedRecord = recordWithPrompt("analysis-1", "Updated prompt after chat");
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), originalRecord);
        var handler = new CapturingChatHandler("incident-analysis", updatedRecord);
        var service = new AnalysisRunHistoryService(store, List.of(handler));

        var response = service.sendChatMessage(
                " analysis-1 ",
                new LocalAnalysisRunChatMessageRequest(" Dopytaj o repo. ")
        );

        assertEquals("analysis-1", handler.indexEntry.analysisId());
        assertEquals(originalRecord, handler.record);
        assertEquals("Dopytaj o repo.", handler.message);
        assertEquals(updatedRecord, store.records.get("analysis-1"));
        assertEquals(CHAT_UPDATED_AT, store.entries.get("analysis-1").updatedAt());
        assertEquals(CHAT_UPDATED_AT, response.updatedAt());
        assertEquals("Updated prompt after chat", response.exportEnvelope().at("/payload/job/preparedPrompt").asText());
    }

    @Test
    void shouldRejectChatWhenContinuationIsDisabled() {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), record("analysis-1", false));
        var handler = new CapturingChatHandler("incident-analysis", record("analysis-1", true));
        var service = new AnalysisRunHistoryService(store, List.of(handler));

        assertThrows(LocalAnalysisRunContinuationUnavailableException.class,
                () -> service.sendChatMessage("analysis-1", new LocalAnalysisRunChatMessageRequest("Dopytaj")));

        assertNull(handler.message);
    }

    @Test
    void shouldRejectChatWhenFeatureHasNoHandler() {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("flow-1", "flow-explorer", "GET /customers"), record("flow-1", true));
        var service = new AnalysisRunHistoryService(store, List.of());

        assertThrows(LocalAnalysisRunContinuationUnavailableException.class,
                () -> service.sendChatMessage("flow-1", new LocalAnalysisRunChatMessageRequest("Dopytaj")));
    }

    @Test
    void shouldMapHandlerChatFailureAndKeepRecordUnchanged() {
        var store = new CapturingLocalAnalysisRunStore();
        var originalRecord = record("analysis-1", true);
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), originalRecord);
        var handler = new CapturingChatHandler(
                "incident-analysis",
                LocalAnalysisRunContinuationException.chatFailed("Copilot unavailable.", null)
        );
        var service = new AnalysisRunHistoryService(store, List.of(handler));

        assertThrows(LocalAnalysisRunChatFailedException.class,
                () -> service.sendChatMessage("analysis-1", new LocalAnalysisRunChatMessageRequest("Dopytaj")));

        assertEquals(originalRecord, store.records.get("analysis-1"));
        assertEquals(UPDATED_AT, store.entries.get("analysis-1").updatedAt());
    }

    @Test
    void shouldRejectRenameForMissingRun() {
        var store = new CapturingLocalAnalysisRunStore();
        var service = new AnalysisRunHistoryService(store);

        assertThrows(LocalAnalysisRunNotFoundException.class,
                () -> service.renameRun("missing", "New name"));

        assertNull(store.renamedAnalysisId);
        assertNull(store.renamedName);
    }

    @Test
    void shouldDeleteExistingRun() {
        var store = new CapturingLocalAnalysisRunStore();
        store.addRun(entry("analysis-1", "incident-analysis", "corr-123"), record("analysis-1", true));
        var service = new AnalysisRunHistoryService(store);

        service.deleteRun(" analysis-1 ");

        assertEquals("analysis-1", store.deletedAnalysisId);
        assertTrue(store.entries.isEmpty());
        assertTrue(store.records.isEmpty());
    }

    @Test
    void shouldReportCorruptedRunWhenIndexExistsButRecordIsMissing() {
        var store = new CapturingLocalAnalysisRunStore();
        store.addIndexOnly(entry("analysis-1", "incident-analysis", "corr-123"));
        var service = new AnalysisRunHistoryService(store);

        assertThrows(LocalAnalysisRunCorruptedException.class, () -> service.getRun("analysis-1"));
    }

    private LocalAnalysisRunIndexEntry entry(String analysisId, String feature, String name) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + analysisId + "/run.json",
                feature,
                name,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT
        );
    }

    private LocalAnalysisRunRecord record(String analysisId, boolean continuationEnabled) {
        return recordWithPrompt(analysisId, "Prepared prompt", continuationEnabled);
    }

    private LocalAnalysisRunRecord recordWithPrompt(String analysisId, String preparedPrompt) {
        return recordWithPrompt(analysisId, preparedPrompt, true);
    }

    private LocalAnalysisRunRecord recordWithPrompt(
            String analysisId,
            String preparedPrompt,
            boolean continuationEnabled
    ) {
        return recordWithPrompt(
                analysisId,
                preparedPrompt,
                new LocalAnalysisRunContinuation(continuationEnabled, "CRM/runtime", "LOCAL_TOKEN", "local-token")
        );
    }

    private LocalAnalysisRunRecord recordWithPrompt(
            String analysisId,
            String preparedPrompt,
            LocalAnalysisRunContinuation continuation
    ) {
        var envelope = objectMapper.createObjectNode();
        envelope.put("schema", "tdw.analysis-export");
        envelope.put("version", 6);
        envelope.put("exportedAt", COMPLETED_AT.toString());
        var payload = envelope.putObject("payload");
        payload.put("type", "analysis-job");
        var job = payload.putObject("job");
        job.put("analysisId", analysisId);
        job.put("correlationId", "corr-123");
        job.put("status", "COMPLETED");
        job.put("preparedPrompt", preparedPrompt);

        return LocalAnalysisRunRecord.v1(
                envelope,
                continuation
        );
    }

    private static final class CapturingLocalAnalysisRunStore implements LocalAnalysisRunStore {

        private final Map<String, LocalAnalysisRunIndexEntry> entries = new LinkedHashMap<>();
        private final Map<String, LocalAnalysisRunRecord> records = new LinkedHashMap<>();
        private int findByIdCalls;
        private String renamedAnalysisId;
        private String renamedName;
        private String deletedAnalysisId;

        void addRun(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
            entries.put(indexEntry.analysisId(), indexEntry);
            records.put(indexEntry.analysisId(), record);
        }

        void addIndexOnly(LocalAnalysisRunIndexEntry indexEntry) {
            entries.put(indexEntry.analysisId(), indexEntry);
        }

        @Override
        public List<LocalAnalysisRunIndexEntry> listRuns() {
            return new ArrayList<>(entries.values());
        }

        @Override
        public Optional<LocalAnalysisRunRecord> findById(String analysisId) {
            findByIdCalls++;
            return Optional.ofNullable(records.get(analysisId));
        }

        @Override
        public void save(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
            addRun(indexEntry, record);
        }

        @Override
        public void rename(String analysisId, String name) {
            renamedAnalysisId = analysisId;
            renamedName = name;
            entries.computeIfPresent(analysisId, (id, entry) -> entry.withName(name, RENAMED_AT));
        }

        @Override
        public void delete(String analysisId) {
            deletedAnalysisId = analysisId;
            entries.remove(analysisId);
            records.remove(analysisId);
        }
    }

    private static final class CapturingChatHandler implements LocalAnalysisRunChatHandler {

        private final String feature;
        private final LocalAnalysisRunRecord updatedRecord;
        private final LocalAnalysisRunContinuationException exception;
        private LocalAnalysisRunIndexEntry indexEntry;
        private LocalAnalysisRunRecord record;
        private String message;

        private CapturingChatHandler(String feature, LocalAnalysisRunRecord updatedRecord) {
            this.feature = feature;
            this.updatedRecord = updatedRecord;
            this.exception = null;
        }

        private CapturingChatHandler(String feature, LocalAnalysisRunContinuationException exception) {
            this.feature = feature;
            this.updatedRecord = null;
            this.exception = exception;
        }

        @Override
        public String feature() {
            return feature;
        }

        @Override
        public LocalAnalysisRunChatResult continueRun(
                LocalAnalysisRunIndexEntry indexEntry,
                LocalAnalysisRunRecord record,
                String message
        ) {
            this.indexEntry = indexEntry;
            this.record = record;
            this.message = message;
            if (exception != null) {
                throw exception;
            }
            return new LocalAnalysisRunChatResult(updatedRecord, CHAT_UPDATED_AT);
        }
    }
}
