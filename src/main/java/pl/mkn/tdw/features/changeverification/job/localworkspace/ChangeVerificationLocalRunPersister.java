package pl.mkn.tdw.features.changeverification.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.features.changeverification.job.export.ChangeVerificationExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunSnapshotWriter;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

@Component
@RequiredArgsConstructor
public class ChangeVerificationLocalRunPersister implements ChangeVerificationLocalRunPersistence {

    static final String FEATURE = "change-verification";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;

    @Override
    public void persistRunSnapshot(ChangeVerificationJobStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        var exportEnvelope = ChangeVerificationExportEnvelope.from(snapshot,
                LocalAnalysisRunSnapshotWriter.exportTimestamp(snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt()));
        LocalAnalysisRunSnapshotWriter.save(localAnalysisRunStore, objectMapper.valueToTree(exportEnvelope),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null),
                new LocalAnalysisRunSnapshotWriter.Metadata(snapshot.jobId(), FEATURE, displayName(snapshot),
                        snapshot.status(), snapshot.createdAt(), snapshot.updatedAt(), snapshot.completedAt()));
    }

    private String displayName(ChangeVerificationJobStateSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.issueKey())) {
            return snapshot.issueKey();
        }
        if (StringUtils.hasText(snapshot.issueUrl())) {
            return snapshot.issueUrl();
        }
        return StringUtils.hasText(snapshot.jobId()) ? snapshot.jobId() : "Change Verification run";
    }

}
