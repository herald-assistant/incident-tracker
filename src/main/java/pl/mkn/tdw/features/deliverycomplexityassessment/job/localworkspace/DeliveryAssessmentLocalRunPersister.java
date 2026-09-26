package pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.export.DeliveryComplexityAssessmentExportEnvelope;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunSnapshotWriter;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

@Component
@RequiredArgsConstructor
public class DeliveryAssessmentLocalRunPersister implements DeliveryAssessmentLocalRunPersistence {

    public static final String FEATURE = "delivery-complexity-assessment";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore store;
    private final LocalWorkspaceProperties workspaceProperties;

    @Override
    public void persistRunSnapshot(DeliveryComplexityAssessmentJobStateSnapshot snapshot) {
        if (!workspaceProperties.isEnabled()) {
            throw new IllegalStateException("Local workspace is disabled.");
        }
        var envelope = DeliveryComplexityAssessmentExportEnvelope.from(snapshot,
                LocalAnalysisRunSnapshotWriter.exportTimestamp(snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt()));
        LocalAnalysisRunSnapshotWriter.save(store, objectMapper.valueToTree(envelope),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null),
                new LocalAnalysisRunSnapshotWriter.Metadata(snapshot.jobId(), FEATURE,
                        snapshot.jiraProject() + " | " + snapshot.fromDate() + " - " + snapshot.toDate(),
                        snapshot.status(), snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt()));
    }
}
