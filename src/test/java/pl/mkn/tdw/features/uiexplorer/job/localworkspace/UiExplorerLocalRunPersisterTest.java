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
import pl.mkn.tdw.features.uiexplorer.report.DefaultUiExplorerResultReportAssembler;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.COMPLETED_AT;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.SOURCE_PATH;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.snapshot;

class UiExplorerLocalRunPersisterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldPersistSanitizedCompletedCrmRunWithoutRawOrHiddenContext() throws Exception {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = persister(store);

        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.COMPLETED));

        assertThat(store.entries).singleElement().satisfies(entry -> {
            assertThat(entry.feature()).isEqualTo("ui-explorer");
            assertThat(entry.name()).isEqualTo("CRM Contact Preferences / CHANGE_PREPARATION");
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
        assertThat(envelope.at("/payload/job/sourceRevision/revision").asText())
                .isEqualTo("crm-commit-abc123");
        assertThat(envelope.at("/payload/job/result/sections/0/sourceReferences/0/repository").isNull()).isTrue();
        assertThat(envelope.at("/payload/job/report/meta/references/0/target").asText())
                .isEqualTo(SOURCE_PATH + "#L10-L18");
        assertThat(envelope.at("/payload/job/toolEvidenceSections/0/items/0/title").asText())
                .isEqualTo("Source evidence: " + SOURCE_PATH);
        var persistedAttributeNames = new ArrayList<String>();
        envelope.at("/payload/job/toolEvidenceSections/0/items/0/attributes")
                .forEach(attribute -> persistedAttributeNames.add(attribute.path("name").asText()));
        assertThat(persistedAttributeNames)
                .containsOnly("filePath", "reason", "toolCallId", "toolName");
        assertThat(envelope.at("/payload/job/aiActivityEvents/0/details").isEmpty()).isTrue();
        assertThat(envelope.at("/payload/job/toolFeedback").isEmpty()).isTrue();
        assertThat(envelope.at("/payload/job/preparedPrompt").isNull()).isTrue();
        assertThat(envelope.at("/payload/job/exportAvailable").asBoolean()).isTrue();

        var serialized = objectMapper.writeValueAsString(record);
        assertThat(serialized)
                .doesNotContain(
                        "CRM_RAW_PROMPT_SECRET",
                        "CRM_RAW_SOURCE_SECRET",
                        "CRM_HIDDEN_SCOPE_SECRET",
                        "CRM_HIDDEN_ACTIVITY_SECRET",
                        "CRM_HIDDEN_FEEDBACK_SECRET",
                        "confidential-crm-group",
                        "internal-crm-ui-project"
                );
    }

    @Test
    void shouldPersistEveryTerminalCrmStatusAndIgnoreRunningSnapshot() {
        var store = new CapturingLocalAnalysisRunStore();
        var persister = persister(store);

        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.COMPLETED));
        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.PARTIAL));
        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.BLOCKED));
        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.FAILED));
        persister.persistTerminalSnapshot(snapshot(UiExplorerJobStatus.ANALYZING));
        persister.persistTerminalSnapshot(null);

        assertThat(store.entries).extracting(LocalAnalysisRunIndexEntry::status)
                .containsExactly("COMPLETED", "PARTIAL", "BLOCKED", "FAILED");
        assertThat(store.records).hasSize(4);
        assertThat(store.records).allSatisfy(record ->
                assertThat(record.continuation().enabled()).isFalse());
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
        persister(firstStore).persistTerminalSnapshot(snapshot);

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
                .contains("strongly anonymized CRM");

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
                new UiExplorerLocalRunSnapshotSanitizer(new DefaultUiExplorerResultReportAssembler())
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
