package pl.mkn.tdw.features.deliveryscopecomplexity.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStateSnapshot;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.export.DeliveryScopeComplexityExportEnvelope;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DeliveryScopeLocalRunPersister implements DeliveryScopeLocalRunPersistence {

    public static final String FEATURE = "delivery-scope-complexity";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore store;
    private final LocalWorkspaceProperties workspaceProperties;

    @Override
    public void persistRunSnapshot(DeliveryScopeComplexityJobStateSnapshot snapshot) {
        if (!workspaceProperties.isEnabled()) {
            throw new IllegalStateException("Local workspace is disabled.");
        }
        var envelope = DeliveryScopeComplexityExportEnvelope.from(snapshot, exportTimestamp(snapshot));
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(envelope),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null)
        );
        store.save(new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                snapshot.jiraProject() + " | " + snapshot.fromDate() + " - " + snapshot.toDate(),
                snapshot.status(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        ), record);
    }

    private Instant exportTimestamp(DeliveryScopeComplexityJobStateSnapshot snapshot) {
        if (snapshot.completedAt() != null) {
            return snapshot.completedAt();
        }
        return snapshot.updatedAt() != null ? snapshot.updatedAt() : snapshot.createdAt();
    }
}
