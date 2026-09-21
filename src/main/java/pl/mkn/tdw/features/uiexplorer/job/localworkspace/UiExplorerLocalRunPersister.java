package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.time.Instant;
import java.util.EnumSet;

@Component
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
    private final UiExplorerContinuationSnapshotStore continuationSnapshotStore;

    @Autowired
    public UiExplorerLocalRunPersister(
            ObjectMapper objectMapper,
            LocalAnalysisRunStore localAnalysisRunStore,
            UiExplorerLocalRunSnapshotSanitizer sanitizer,
            UiExplorerContinuationSnapshotStore continuationSnapshotStore
    ) {
        this.objectMapper = objectMapper;
        this.localAnalysisRunStore = localAnalysisRunStore;
        this.sanitizer = sanitizer;
        this.continuationSnapshotStore = continuationSnapshotStore;
    }

    UiExplorerLocalRunPersister(
            ObjectMapper objectMapper,
            LocalAnalysisRunStore localAnalysisRunStore,
            UiExplorerLocalRunSnapshotSanitizer sanitizer
    ) {
        this(objectMapper, localAnalysisRunStore, sanitizer, null);
    }

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

    @Override
    public void persistRunSnapshot(
            UiExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId,
            UiExplorerScreenReachabilityContext context
    ) {
        if (snapshot == null || snapshot.status() == null || !TERMINAL_STATUSES.contains(snapshot.status())) {
            return;
        }
        var sanitizedSnapshot = sanitizer.sanitize(snapshot);
        var continuation = continuation(snapshot, authRef, copilotSessionId, context);
        if (continuation.enabled()) {
            if (continuationSnapshotStore == null) {
                throw new IllegalStateException("UI Explorer continuation snapshot store is unavailable.");
            }
            continuationSnapshotStore.save(UiExplorerContinuationSnapshot.from(snapshot, context, storedAt(snapshot)));
        }
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(UiExplorerLocalRunEnvelope.from(sanitizedSnapshot, storedAt(sanitizedSnapshot))),
                continuation
        );
        localAnalysisRunStore.save(indexEntry(sanitizedSnapshot), record);
    }

    private LocalAnalysisRunContinuation continuation(
            UiExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId,
            UiExplorerScreenReachabilityContext context
    ) {
        var eligibleStatus = snapshot.status() == UiExplorerJobStatus.COMPLETED
                || snapshot.status() == UiExplorerJobStatus.PARTIAL;
        var enabled = eligibleStatus
                && snapshot.result() != null
                && snapshot.report() != null
                && StringUtils.hasText(copilotSessionId)
                && context != null
                && context.sourceScope() != null
                && context.sourceRevision() != null;
        if (!enabled) {
            return new LocalAnalysisRunContinuation(false, null, null, null, null, null, null);
        }
        return new LocalAnalysisRunContinuation(
                true,
                context.sourceScope().gitLabGroup(),
                authRef != null ? authRef.mode() : AnalysisAiAuthRef.MODE_LOCAL_TOKEN,
                authRef != null ? authRef.principalId() : null,
                copilotSessionId.trim(),
                LocalAnalysisRunContinuation.COPILOT_RUNTIME_GITHUB_COPILOT_SDK,
                LocalAnalysisRunContinuation.CONTINUATION_MODE_COPILOT_SESSION
        );
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
