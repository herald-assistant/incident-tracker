package pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.state.DeliveryComplexityAssessmentJobState;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryAssessmentLocalRunPersisterTest {

    @Test
    void shouldPersistQueuedSnapshotUnderStableFeatureId() {
        var store = mock(LocalAnalysisRunStore.class);
        var workspace = new LocalWorkspaceProperties();
        var persister = new DeliveryAssessmentLocalRunPersister(
                new ObjectMapper().registerModule(new JavaTimeModule()), store, workspace
        );
        var state = new DeliveryComplexityAssessmentJobState(
                "job-1",
                new DeliveryComplexityAssessmentJobStartRequest(
                        "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                        "gpt-5", "medium"
                )
        );

        persister.persistRunSnapshot(state.snapshot());

        var indexCaptor = ArgumentCaptor.forClass(LocalAnalysisRunIndexEntry.class);
        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(indexCaptor.capture(), recordCaptor.capture());
        assertThat(indexCaptor.getValue()).satisfies(index -> {
            assertThat(index.analysisId()).isEqualTo("job-1");
            assertThat(index.feature()).isEqualTo("delivery-complexity-assessment");
            assertThat(index.status()).isEqualTo("QUEUED");
            assertThat(index.runPath()).isEqualTo("runs/job-1/run.json");
        });
        assertThat(recordCaptor.getValue().exportEnvelope().path("schema").asText())
                .isEqualTo("tdw.delivery-complexity-assessment-export");
        assertThat(recordCaptor.getValue().exportEnvelope().path("payload").path("job").path("status").asText())
                .isEqualTo("QUEUED");
        assertThat(recordCaptor.getValue().continuation().enabled()).isFalse();
    }
}
