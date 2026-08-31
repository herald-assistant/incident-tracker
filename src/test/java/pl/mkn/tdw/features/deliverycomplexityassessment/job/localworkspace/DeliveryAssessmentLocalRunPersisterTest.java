package pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.state.DeliveryComplexityAssessmentJobState;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentSourceResult;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.unit;

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

    @Test
    void shouldPersistRawAiResponseInV1Envelope() {
        var store = mock(LocalAnalysisRunStore.class);
        var persister = new DeliveryAssessmentLocalRunPersister(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                store,
                new LocalWorkspaceProperties()
        );
        var state = new DeliveryComplexityAssessmentJobState(
                "job-1",
                new DeliveryComplexityAssessmentJobStartRequest(
                        "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                        "gpt-5", "medium"
                )
        );
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        state.markDiscoveryStarted();
        state.markUnitsReady(
                new DeliveryAssessmentSourceResult("jql", 1, false, List.of(), List.of()),
                List.of(deliveryUnit)
        );
        state.markUnitAnalyzing(deliveryUnit.unitId());
        state.markUnitRawAiResponse(deliveryUnit.unitId(), "not valid JSON");

        persister.persistRunSnapshot(state.snapshot());

        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(any(), recordCaptor.capture());
        var envelope = recordCaptor.getValue().exportEnvelope();
        assertThat(envelope.path("version").asInt()).isEqualTo(1);
        assertThat(envelope.path("payload").path("resultContract").asText())
                .isEqualTo("delivery-complexity-assessment-v1");
        assertThat(envelope.path("payload").path("job").path("units").path(0).path("rawAiResponse").asText())
                .isEqualTo("not valid JSON");
        var issue = envelope.path("payload").path("job").path("units").path(0).path("issues").path(0);
        assertThat(issue.path("timeSpentSeconds").asLong()).isEqualTo(14400L);
        assertThat(issue.path("originalEstimateSeconds").asLong()).isEqualTo(28800L);
        assertThat(issue.path("remainingEstimateSeconds").asLong()).isEqualTo(7200L);
        assertThat(issue.path("timeTrackingCapturedAt").asDouble()).isEqualTo(1783756800.0);
    }
}
