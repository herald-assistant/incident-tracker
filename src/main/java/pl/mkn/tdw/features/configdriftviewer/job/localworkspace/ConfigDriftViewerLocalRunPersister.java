package pl.mkn.tdw.features.configdriftviewer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.export.ConfigDriftViewerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerLocalRunPersister
        implements ConfigDriftViewerLocalRunPersistence {

    public static final String FEATURE = "config-drift-viewer";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;

    @Override
    public void persistRunSnapshot(ConfigDriftViewerJobStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        var envelope = ConfigDriftViewerExportEnvelope.from(
                snapshot,
                exportTimestamp(snapshot)
        );
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(envelope),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null)
        );
        localAnalysisRunStore.save(indexEntry(snapshot), record);
    }

    private LocalAnalysisRunIndexEntry indexEntry(ConfigDriftViewerJobStateSnapshot snapshot) {
        return new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                snapshot.systemIds().size() + " komponentów · " + snapshot.sourceBranch() + " → "
                        + snapshot.targetBranch() + " · " + snapshot.mode(),
                snapshot.status(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
    }

    private Instant exportTimestamp(ConfigDriftViewerJobStateSnapshot snapshot) {
        if (snapshot.completedAt() != null) {
            return snapshot.completedAt();
        }
        if (snapshot.updatedAt() != null) {
            return snapshot.updatedAt();
        }
        return snapshot.createdAt();
    }
}
