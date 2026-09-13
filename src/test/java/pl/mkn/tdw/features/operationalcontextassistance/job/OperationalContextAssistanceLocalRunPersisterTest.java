package pl.mkn.tdw.features.operationalcontextassistance.job;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace.OperationalContextAssistanceLocalRunPersister;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationalContextAssistanceLocalRunPersisterTest {

    @TempDir Path workspace;

    @Test
    void savesReviewableSnapshotWithoutRawRequestOrContinuationCredentials() {
        var store = mock(LocalAnalysisRunStore.class);
        var persister = new OperationalContextAssistanceLocalRunPersister(
                JsonMapper.builder().findAndAddModules().build(), store
        );
        var state = new OperationalContextAssistanceJobState("assistance-123");
        state.start();
        state.contextCollected("digest-1", null, List.of(), 0);
        state.prepared("sanitized canonical prompt", List.of("opctx:systems.yml"), List.of());
        state.failed("AI_FAILED", "Asysta nie zakończyła się poprawnie.");
        var request = new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA,
                "raw user description that should not be saved separately",
                null, null, null, null, null
        );

        persister.persistRunSnapshot(state.snapshot(), request);

        var indexCaptor = ArgumentCaptor.forClass(LocalAnalysisRunIndexEntry.class);
        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(indexCaptor.capture(), recordCaptor.capture());
        var index = indexCaptor.getValue();
        var record = recordCaptor.getValue();
        assertThat(index.feature()).isEqualTo(OperationalContextAssistanceLocalRunPersister.FEATURE);
        assertThat(index.status()).isEqualTo("FAILED");
        assertThat(index.name()).isEqualTo("Utwórz lub uzupełnij katalog");
        assertThat(record.continuation().enabled()).isFalse();
        assertThat(record.exportEnvelope().path("schema").asText())
                .isEqualTo("tdw.operational-context-assistance-export");
        assertThat(record.exportEnvelope().path("mode").asText()).isEqualTo("CREATE_AREA");
        assertThat(record.exportEnvelope().path("job").path("preparedPrompt").asText())
                .isEqualTo("sanitized canonical prompt");
        assertThat(record.exportEnvelope().toString()).doesNotContain("raw user description");
    }

    @Test
    void canReadSavedRunAfterReconstructingLocalStore() {
        var mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var properties = new LocalWorkspaceProperties();
        properties.setDirectory(workspace.toString());
        var paths = new LocalWorkspacePaths(properties);
        var store = new FileSystemLocalAnalysisRunStore(
                properties, paths, new LocalWorkspaceJsonFileStore(mapper));
        var state = new OperationalContextAssistanceJobState("assistance-456");
        state.start();
        state.contextCollected("digest-1", null, List.of(), 0);
        state.prepared("Prompt do późniejszego wglądu", List.of(), List.of());
        state.failed("AI_FAILED", "Asysta nie zakończyła się poprawnie.");
        var request = new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Uzupełnij katalog.",
                null, null, null, null, null);

        new OperationalContextAssistanceLocalRunPersister(mapper, store)
                .persistRunSnapshot(state.snapshot(), request);

        var reopenedStore = new FileSystemLocalAnalysisRunStore(
                properties, new LocalWorkspacePaths(properties), new LocalWorkspaceJsonFileStore(mapper));
        assertThat(reopenedStore.listRuns()).extracting(LocalAnalysisRunIndexEntry::analysisId)
                .containsExactly("assistance-456");
        var reopened = reopenedStore.findById("assistance-456").orElseThrow();
        assertThat(reopened.exportEnvelope().path("job").path("preparedPrompt").asText())
                .isEqualTo("Prompt do późniejszego wglądu");
        assertThat(reopened.exportEnvelope().path("job").path("status").asText()).isEqualTo("FAILED");
    }
}
