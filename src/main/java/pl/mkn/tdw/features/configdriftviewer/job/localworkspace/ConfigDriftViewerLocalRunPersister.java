package pl.mkn.tdw.features.configdriftviewer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.export.ConfigDriftViewerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunSnapshotWriter;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

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
                LocalAnalysisRunSnapshotWriter.exportTimestamp(snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt())
        );
        LocalAnalysisRunSnapshotWriter.save(localAnalysisRunStore, objectMapper.valueToTree(envelope),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null),
                new LocalAnalysisRunSnapshotWriter.Metadata(snapshot.jobId(), FEATURE,
                        snapshot.systemIds().size() + " komponentów · " + snapshot.sourceBranch() + " → "
                                + snapshot.targetBranch() + " · " + snapshot.mode(),
                        snapshot.status(), snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt()));
    }
}
