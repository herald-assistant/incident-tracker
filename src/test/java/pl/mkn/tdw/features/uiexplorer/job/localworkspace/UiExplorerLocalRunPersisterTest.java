package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.api.analysisruns.AnalysisRunHistoryService;
import pl.mkn.tdw.api.analysisruns.LocalAnalysisRunCorruptedException;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionCleanup;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.state.UiExplorerJobState;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.COMPLETED_AT;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.PREPARED_PROMPT;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.SOURCE_PATH;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.snapshot;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerLocalRunPersisterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldPersistResumableCrmSessionAndPrivateScopeAfterRestart(@TempDir Path workspace) throws Exception {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(true);
        properties.setDirectory(workspace.resolve("crm-ui-continuation").toString());
        var paths = new LocalWorkspacePaths(properties);
        var jsonFileStore = new LocalWorkspaceJsonFileStore(objectMapper);
        var store = new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore);
        var continuationStore = new UiExplorerContinuationSnapshotStore(paths, jsonFileStore);
        var persister = new UiExplorerLocalRunPersister(
                objectMapper, store, new UiExplorerLocalRunSnapshotSanitizer(), continuationStore);
        var completed = snapshot(UiExplorerJobStatus.COMPLETED);

        persister.persistRunSnapshot(
                completed,
                AnalysisAiAuthRef.localToken(null),
                "crm-ui-copilot-session",
                context()
        );

        var record = store.findById(completed.jobId()).orElseThrow();
        assertThat(record.continuation().enabled()).isTrue();
        assertThat(record.continuation().copilotSessionId()).isEqualTo("crm-ui-copilot-session");
        var restored = continuationStore.findById(completed.jobId()).orElseThrow();
        assertThat(restored.toContext().sourceRevision().branch()).isEqualTo("main");
        assertThat(restored.toContext().sourceScope().projectName()).isEqualTo("crm-agent-portal");
        assertThat(restored.fingerprint()).hasSize(64);
        assertThat(restored.matches(completed)).isTrue();
        assertThat(Files.readString(paths.runDirectory(completed.jobId())
                .resolve("ui-explorer-continuation.json")))
                .doesNotContain("CustomerPreferencesComponent {}")
                .doesNotContain("preparedPrompt");
    }

    @Test
    void shouldPersistSanitizedCompletedCrmRunWithoutRawOrHiddenContext() throws Exception {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = persister(store);

        persister.persistSnapshot(snapshot(UiExplorerJobStatus.COMPLETED));

        assertThat(store.entries).singleElement().satisfies(entry -> {
            assertThat(entry.feature()).isEqualTo("ui-explorer");
            assertThat(entry.name()).isEqualTo("CRM Contact Preferences");
            assertThat(entry.status()).isEqualTo("COMPLETED");
            assertThat(entry.completedAt()).isEqualTo(COMPLETED_AT);
        });
        var record = store.records.get(0);
        assertThat(record.continuation().enabled()).isFalse();
        assertThat(record.continuation().copilotSessionId()).isNull();
        var envelope = record.exportEnvelope();
        assertThat(envelope.path("schema").asText()).isEqualTo("tdw.ui-explorer-local-run");
        assertThat(envelope.path("version").asInt()).isEqualTo(1);
        assertThat(envelope.at("/payload/type").asText()).isEqualTo("ui-explorer-analysis");
        assertThat(envelope.at("/payload/resultContract").asText()).isEqualTo("ui-explorer-result-v1");
        assertThat(envelope.at("/payload/job/sourceRevision/branch").asText())
                .isEqualTo("main");
        assertThat(envelope.at("/payload/job/result/sections/0/sourceReferences/0/repository").isNull()).isTrue();
        assertThat(envelope.at("/payload/job/report/meta/references/0/target").asText())
                .isEqualTo(SOURCE_PATH + "#L10-L18");
        assertThat(envelope.at("/payload/job/toolEvidenceSections/0/items/0/title").asText())
                .isEqualTo("Source evidence: " + SOURCE_PATH);
        var persistedAttributeNames = new ArrayList<String>();
        envelope.at("/payload/job/toolEvidenceSections/0/items/0/attributes")
                .forEach(attribute -> persistedAttributeNames.add(attribute.path("name").asText()));
        assertThat(persistedAttributeNames)
                .containsOnly("filePath", "reason", "toolCallId", "toolName", "content");
        assertThat(envelope.at("/payload/job/toolEvidenceSections/0/items/0/attributes/4/value").asText())
                .isEqualTo("export class CustomerPreferencesComponent {}");
        assertThat(envelope.at("/payload/job/toolEvidenceSections/0/items/1/title").asText())
                .isEqualTo("UI Explorer tool evidence");
        assertThat(envelope.at("/payload/job/toolEvidenceSections/0/items/1/attributes").toString())
                .contains("filePaths")
                .contains("src/app/crm/customer-preferences.ts")
                .contains("src/app/crm/customer-preferences.html")
                .doesNotContain("projectName");
        assertThat(envelope.at("/payload/job/aiActivityEvents/0/details/success").asBoolean()).isTrue();
        assertThat(envelope.at("/payload/job/aiActivityEvents/0/details/resultContent/value").toString())
                .contains("CustomerPreferencesComponent")
                .contains(SOURCE_PATH);
        assertThat(envelope.at("/payload/job/aiActivityEvents/0/details/rawArguments").isMissingNode()).isTrue();
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/type").asText())
                .isEqualTo("platform.context_tier");
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/phase").asText())
                .isEqualTo("TIER_REQUESTED");
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/trigger").asText())
                .isEqualTo("FEATURE_REQUIREMENT");
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/requestedTier").asText())
                .isEqualTo("long_context");
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/estimatedInitialTokens").asLong())
                .isEqualTo(96_000L);
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/runtimeUsageThreshold").asDouble())
                .isEqualTo(0.70D);
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/runtimeThresholdTokens").asLong())
                .isEqualTo(190_400L);
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/sessionId").asText())
                .isEqualTo("crm-anonymized-session-17");
        assertThat(envelope.at("/payload/job/aiActivityEvents/1/details/rawArguments").isMissingNode()).isTrue();
        assertThat(envelope.at("/payload/job/toolFeedback").isEmpty()).isTrue();
        assertThat(envelope.at("/payload/job/preparedPrompt").asText()).isEqualTo(PREPARED_PROMPT);
        assertThat(envelope.at("/payload/job/exportAvailable").asBoolean()).isTrue();

        var serialized = objectMapper.writeValueAsString(record);
        assertThat(serialized)
                .doesNotContain(
                        "CRM_RAW_SOURCE_SECRET",
                        "CRM_HIDDEN_SCOPE_SECRET",
                        "CRM_HIDDEN_ACTIVITY_SECRET",
                        "CRM_HIDDEN_CONTEXT_TIER_SECRET",
                        "CRM_HIDDEN_FEEDBACK_SECRET",
                        "confidential-crm-group",
                        "internal-crm-ui-project"
                );
    }

    @Test
    void shouldPersistQueuedRunningAndTerminalCrmSnapshots() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = persister(store);

        persister.persistSnapshot(new UiExplorerJobState("crm-queued-run", request()).snapshot());
        persister.persistSnapshot(snapshot(UiExplorerJobStatus.COMPLETED));
        persister.persistSnapshot(snapshot(UiExplorerJobStatus.PARTIAL));
        persister.persistSnapshot(snapshot(UiExplorerJobStatus.BLOCKED));
        persister.persistSnapshot(snapshot(UiExplorerJobStatus.FAILED));
        persister.persistSnapshot(snapshot(UiExplorerJobStatus.ANALYZING));
        persister.persistSnapshot(null);

        assertThat(store.entries).extracting(LocalAnalysisRunIndexEntry::status)
                .containsExactly("QUEUED", "COMPLETED", "PARTIAL", "BLOCKED", "FAILED", "ANALYZING");
        assertThat(store.records).hasSize(6);
        assertThat(store.records.get(0).exportEnvelope().at("/payload/job/report").isNull()).isTrue();
        assertThat(store.records).allSatisfy(record ->
                assertThat(record.continuation().enabled()).isFalse());
    }

    @Test
    void shouldUpdateTheSameCrmHistoryEntryWhenAnalysisFails(@TempDir Path workspace) {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(true);
        properties.setDirectory(workspace.toString());
        var store = new FileSystemLocalAnalysisRunStore(properties, new LocalWorkspacePaths(properties),
                new LocalWorkspaceJsonFileStore(objectMapper));
        var persister = persister(store);
        var job = new UiExplorerJobState("crm-run-1", request());

        persister.persistSnapshot(job.snapshot());
        assertThat(store.listRuns()).singleElement().satisfies(entry ->
                assertThat(entry.status()).isEqualTo("QUEUED"));
        job.markFailed("CRM_SYNTHETIC_FAILURE", "Synthetic CRM failure");
        persister.persistSnapshot(job.snapshot());

        assertThat(store.listRuns()).singleElement().satisfies(entry -> {
            assertThat(entry.analysisId()).isEqualTo("crm-run-1");
            assertThat(entry.status()).isEqualTo("FAILED");
        });
        assertThat(store.findById("crm-run-1").orElseThrow().exportEnvelope()
                .at("/payload/job/status").asText()).isEqualTo("FAILED");
    }

    @Test
    void shouldReadCrmHistoryAfterRestartAndReportCorruptedRun(@TempDir Path workspace) throws Exception {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(true);
        properties.setDirectory(workspace.resolve("crm-ui-history").toString());
        var paths = new LocalWorkspacePaths(properties);
        var jsonFileStore = new LocalWorkspaceJsonFileStore(objectMapper);
        var firstStore = new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore);
        var snapshot = snapshot(UiExplorerJobStatus.COMPLETED);
        persister(firstStore).persistSnapshot(snapshot);

        var restartedStore = new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore);
        var historyService = new AnalysisRunHistoryService(
                restartedStore,
                List.of(),
                CopilotSessionCleanup.NO_OP
        );
        var list = historyService.listRuns();
        var detail = historyService.getRun(snapshot.jobId());

        assertThat(list.runs()).singleElement().satisfies(run -> {
            assertThat(run.feature()).isEqualTo("ui-explorer");
            assertThat(run.status()).isEqualTo("COMPLETED");
        });
        assertThat(detail.feature()).isEqualTo("ui-explorer");
        assertThat(detail.continuationEnabled()).isFalse();
        assertThat(detail.exportEnvelope().at("/payload/job/result/functionalOverview").asText())
                .isEqualTo("Doradca CRM utrzymuje dozwolony kanal kontaktu dla wybranego klienta.");
        assertThat(detail.exportEnvelope().at("/payload/job/preparedPrompt").asText())
                .isEqualTo(PREPARED_PROMPT);

        Files.writeString(paths.runFile(snapshot.jobId()), "{broken synthetic CRM history");
        var afterCorruption = new AnalysisRunHistoryService(
                new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore),
                List.of(),
                CopilotSessionCleanup.NO_OP
        );
        assertThatThrownBy(() -> afterCorruption.getRun(snapshot.jobId()))
                .isInstanceOf(LocalAnalysisRunCorruptedException.class);
    }

    private UiExplorerLocalRunPersister persister(LocalAnalysisRunStore store) {
        return new UiExplorerLocalRunPersister(
                objectMapper,
                store,
                new UiExplorerLocalRunSnapshotSanitizer()
        );
    }

    private static final class CapturingLocalAnalysisRunStore implements LocalAnalysisRunStore {

        private final List<LocalAnalysisRunIndexEntry> entries = new ArrayList<>();
        private final List<LocalAnalysisRunRecord> records = new ArrayList<>();

        @Override
        public List<LocalAnalysisRunIndexEntry> listRuns() {
            return List.copyOf(entries);
        }

        @Override
        public Optional<LocalAnalysisRunRecord> findById(String analysisId) {
            for (var index = 0; index < entries.size(); index++) {
                if (entries.get(index).analysisId().equals(analysisId)) {
                    return Optional.of(records.get(index));
                }
            }
            return Optional.empty();
        }

        @Override
        public void save(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
            entries.add(indexEntry);
            records.add(record);
        }

        @Override
        public void rename(String analysisId, String name) {
        }

        @Override
        public void delete(String analysisId) {
        }
    }
}
