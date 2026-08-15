package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class UiExplorerLocalRunPersister implements UiExplorerLocalRunPersistence {

    static final String FEATURE = "ui-explorer";
    private static final EnumSet<UiExplorerJobStatus> TERMINAL_STATUSES = EnumSet.of(
            UiExplorerJobStatus.COMPLETED,
            UiExplorerJobStatus.PARTIAL,
            UiExplorerJobStatus.BLOCKED,
            UiExplorerJobStatus.FAILED
    );

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;
    private final UiExplorerLocalRunSnapshotSanitizer sanitizer;

    @Override
    public void persistTerminalSnapshot(UiExplorerJobStateSnapshot snapshot) {
        if (snapshot == null || snapshot.status() == null || !TERMINAL_STATUSES.contains(snapshot.status())) {
            return;
        }
        var sanitizedSnapshot = sanitizer.sanitize(snapshot);
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(UiExplorerLocalRunEnvelope.from(
                        sanitizedSnapshot,
                        storedAt(sanitizedSnapshot)
                )),
                new LocalAnalysisRunContinuation(false, null, null, null, null, null, null)
        );
        localAnalysisRunStore.save(indexEntry(sanitizedSnapshot), record);
    }

    private LocalAnalysisRunIndexEntry indexEntry(UiExplorerJobStateSnapshot snapshot) {
        return new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                displayName(snapshot),
                snapshot.status().name(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
    }

    private String displayName(UiExplorerJobStateSnapshot snapshot) {
        var screenLabel = snapshot.result() != null && snapshot.result().screen() != null
                ? snapshot.result().screen().label()
                : null;
        if (!StringUtils.hasText(screenLabel) && snapshot.request() != null) {
            screenLabel = snapshot.request().screenId();
        }
        var profile = snapshot.request() != null && snapshot.request().profile() != null
                ? snapshot.request().profile().name()
                : null;
        if (StringUtils.hasText(screenLabel) && StringUtils.hasText(profile)) {
            return screenLabel.trim() + " / " + profile;
        }
        if (StringUtils.hasText(screenLabel)) {
            return screenLabel.trim();
        }
        return StringUtils.hasText(snapshot.jobId()) ? snapshot.jobId() : "UI Explorer run";
    }

    private Instant storedAt(UiExplorerJobStateSnapshot snapshot) {
        if (snapshot.completedAt() != null) {
            return snapshot.completedAt();
        }
        if (snapshot.updatedAt() != null) {
            return snapshot.updatedAt();
        }
        return snapshot.createdAt();
    }
}
